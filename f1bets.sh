#!/bin/bash
set -e

APP_NAME="f1bets"
COMPOSE_FILE="docker-compose.yml"

usage() {
    echo "Usage: $0 {start|stop|restart|status|logs|build|test|smoke}"
    echo ""
    echo "Commands:"
    echo "  start     Start the application"
    echo "  stop      Stop the application"
    echo "  restart   Restart the application"
    echo "  status    Show container and health status"
    echo "  logs      Follow application logs"
    echo "  build     Rebuild Docker images"
    echo "  test      Run all 173 unit/integration tests via Docker"
    echo "  smoke     Run 57-assertion smoke test (add --no-cache to bypass cache)"
    exit 1
}

start() {
    echo "Starting $APP_NAME..."
    docker compose -f $COMPOSE_FILE up -d
    echo "Waiting for services to be healthy..."
    sleep 5
    
    for i in {1..30}; do
        if curl -s http://localhost:8090/actuator/health > /dev/null 2>&1; then
            echo "$APP_NAME started successfully!"
            echo "  API:        http://localhost:8090"
            echo "  Swagger:    http://localhost:8090/swagger-ui.html"
            echo "  Health:     http://localhost:8090/actuator/health"
            echo "  PostgreSQL: localhost:${DB_EXTERNAL_PORT:-5432}"
            return 0
        fi
        echo "Waiting for app to be ready... ($i/30)"
        sleep 2
    done
    echo "Warning: App may not be fully ready yet. Check logs with: $0 logs"
}

stop() {
    echo "Stopping $APP_NAME..."
    docker compose -f $COMPOSE_FILE down
    echo "$APP_NAME stopped."
}

restart() {
    stop
    start
}

status() {
    echo "=== Container Status ==="
    docker compose -f $COMPOSE_FILE ps
    echo ""
    echo "=== Database Status ==="
    docker compose -f $COMPOSE_FILE exec -T f1bets-db pg_isready -U f1bets -d f1bets 2>/dev/null || echo "Database not responding"
    echo ""
    echo "=== App Health Check ==="
    curl -s http://localhost:8090/actuator/health 2>/dev/null || echo "App not responding"
}

logs() {
    docker compose -f $COMPOSE_FILE logs -f
}

build() {
    echo "Building $APP_NAME..."
    docker compose -f $COMPOSE_FILE build --no-cache
    echo "Build complete."
}

run_tests() {
    echo "Running unit/integration tests via Docker..."
    local test_project="${APP_NAME}-test"

    # Clean up any leftover containers from previous runs
    docker compose -p "$test_project" -f docker-compose.test.yml down -v --remove-orphans 2>/dev/null || true

    # Run tests with force-recreate to ensure clean state
    docker compose -p "$test_project" -f docker-compose.test.yml up --force-recreate --abort-on-container-exit --exit-code-from test-runner
    local exit_code=$?

    # Always clean up after tests
    docker compose -p "$test_project" -f docker-compose.test.yml down -v --remove-orphans

    return $exit_code
}

smoke() {
    echo "Running 57-assertion smoke test..."
    ./smoke-test.sh "$@"
}

case "$1" in
    start)    start ;;
    stop)     stop ;;
    restart)  restart ;;
    status)   status ;;
    logs)     logs ;;
    build)    build ;;
    test)     run_tests ;;
    smoke)    shift; smoke "$@" ;;
    *)        usage ;;
esac
