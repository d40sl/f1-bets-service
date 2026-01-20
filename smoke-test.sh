#!/bin/bash
#
# F1 Bets API - Comprehensive Smoke Test Suite
# =============================================
# Tests all critical paths, security features, and business logic
#
# Usage: ./smoke-test.sh [--no-cache]
#   --no-cache  Bypass server-side cache (sends Cache-Control: no-cache header)
#

set -e

NO_CACHE=false
DEBUG_CURL=false
SKIP_SETTLEMENT="${SKIP_SETTLEMENT:-false}"
for arg in "$@"; do
    case $arg in
        --no-cache)
            NO_CACHE=true
            shift
            ;;
        --debug)
            DEBUG_CURL=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./smoke-test.sh [--no-cache] [--debug]"
            echo "  --no-cache  Bypass server-side cache for OpenF1 data"
            echo "  --debug     Show curl commands being executed"
            exit 0
            ;;
    esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
DIM='\033[0;90m'
NC='\033[0m'
BOLD='\033[1m'

BASE_URL="${BASE_URL:-http://localhost:8090}"
PASS_COUNT=0
FAIL_COUNT=0

print_header() {
    echo ""
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_section() {
    echo ""
    echo -e "${CYAN}▶ $1${NC}"
    echo -e "${DIM}─────────────────────────────────────────────────────────────────${NC}"
}

print_test() {
    echo -e "${WHITE}  TEST: $1${NC}"
}

print_info() {
    echo -e "${DIM}  ℹ $1${NC}"
}

print_expected() {
    echo -e "${BLUE}  Expected: $1${NC}"
}

print_response() {
    echo -e "${DIM}  $1${NC}"
}

print_pass() {
    echo -e "${GREEN}  ✓ PASS: $1${NC}"
    ((PASS_COUNT++))
}

print_fail() {
    echo -e "${RED}  ✗ FAIL: $1${NC}"
    ((FAIL_COUNT++))
}

print_warning() {
    echo -e "${YELLOW}  ⚠ $1${NC}"
}

print_money() {
    echo -e "${GREEN}  💰 $1${NC}"
}

print_security() {
    echo -e "${RED}  🛡️  $1${NC}"
}

uuid() {
    cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || echo "$(date +%s)-$$-$RANDOM"
}

HTTP_MAX_ATTEMPTS="${HTTP_MAX_ATTEMPTS:-10}"
HTTP_BASE_DELAY="${HTTP_BASE_DELAY:-0.4}"
HTTP_MAX_DELAY="${HTTP_MAX_DELAY:-8}"
HTTP_CONNECT_TIMEOUT="${HTTP_CONNECT_TIMEOUT:-10}"
HTTP_MAX_TIME="${HTTP_MAX_TIME:-300}"
HTTP_USER_AGENT="F1BetsSmokeTest/1.0"
ODDS_SAMPLE_SIZE="${ODDS_SAMPLE_SIZE:-5}"

RETRYABLE_CODES="408 425 429 500 502 503 504"
HTTP_CODE=""
HTTP_BODY=""
HTTP_HEADERS=""

calculate_backoff() {
    local attempt=$1
    local base=$HTTP_BASE_DELAY
    local max=$HTTP_MAX_DELAY
    
    local exp_delay=$(awk -v b="$base" -v a="$attempt" 'BEGIN{printf "%.3f", b * (2 ^ a)}')
    local jitter=$(awk -v d="$exp_delay" 'BEGIN{srand(); printf "%.3f", d * rand() * 0.3}')
    local total=$(awk -v d="$exp_delay" -v j="$jitter" 'BEGIN{printf "%.3f", d + j}')
    awk -v t="$total" -v m="$max" 'BEGIN{printf "%.3f", (t < m) ? t : m}'
}

parse_retry_after() {
    local headers="$1"
    local retry_after=$(echo "$headers" | grep -i "^retry-after:" | head -1 | cut -d: -f2 | tr -d ' \r')
    
    if [ -n "$retry_after" ]; then
        if [[ "$retry_after" =~ ^[0-9]+$ ]]; then
            echo "$retry_after"
        else
            echo ""
        fi
    else
        echo ""
    fi
}

is_retryable_code() {
    local code=$1
    for retryable in $RETRYABLE_CODES; do
        if [ "$code" == "$retryable" ]; then
            return 0
        fi
    done
    return 1
}

# http_request - Resilient HTTP request with retries
# Usage: http_request METHOD URL [OPTIONS...]
# Options:
#   -d DATA           Request body (for POST/PUT)
#   -H "Header: Val"  Add header (can be repeated)
#   --follow          Follow redirects
#   --retry-on-4xx    Retry even on 4xx errors (opt-in)
#   --no-retry        Disable retries entirely
#   --silent          Suppress retry messages
# Sets: HTTP_CODE, HTTP_BODY, HTTP_HEADERS
http_request() {
    local method="$1"
    shift
    local url="$1"
    shift
    
    local data=""
    local headers=()
    local retry_on_4xx=false
    local no_retry=false
    local silent=false
    local follow_redirects=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -d)
                data="$2"
                shift 2
                ;;
            -H)
                headers+=("-H" "$2")
                shift 2
                ;;
            --follow)
                follow_redirects=true
                shift
                ;;
            --retry-on-4xx)
                retry_on_4xx=true
                shift
                ;;
            --no-retry)
                no_retry=true
                shift
                ;;
            --silent)
                silent=true
                shift
                ;;
            *)
                shift
                ;;
        esac
    done
    
    local attempt=0
    local tmp_headers=$(mktemp)
    local tmp_body=$(mktemp)
    
    _http_cleanup() {
        rm -f "$tmp_headers" "$tmp_body" 2>/dev/null || true
    }
    
    while [ $attempt -lt $HTTP_MAX_ATTEMPTS ]; do
        local curl_args=(
            -s
            -X "$method"
            --connect-timeout "$HTTP_CONNECT_TIMEOUT"
            --max-time "$HTTP_MAX_TIME"
            -A "$HTTP_USER_AGENT"
            -D "$tmp_headers"
            -o "$tmp_body"
            -w "%{http_code}"
        )
        
        if [ "$follow_redirects" = true ]; then
            curl_args+=(-L)
        fi
        
        if [ -n "$data" ]; then
            curl_args+=(-d "$data")
        fi
        
        curl_args+=("${headers[@]}")
        curl_args+=("$url")

        # Debug: show curl command
        if [ "${DEBUG_CURL:-false}" = true ]; then
            echo -e "${DIM}  DEBUG: curl ${curl_args[*]}${NC}" >&2
        fi

        local curl_exit_code=0
        HTTP_CODE=$(curl "${curl_args[@]}") || curl_exit_code=$?
        HTTP_BODY=$(cat "$tmp_body" 2>/dev/null || echo "")
        HTTP_HEADERS=$(cat "$tmp_headers" 2>/dev/null || echo "")
        
        if [ $curl_exit_code -ne 0 ]; then
            if [ "$no_retry" = true ]; then
                HTTP_CODE="000"
                return 1
            fi
            
            ((attempt++)) || true
            if [ $attempt -lt $HTTP_MAX_ATTEMPTS ]; then
                local backoff=$(calculate_backoff $attempt)
                [ "$silent" = false ] && echo -e "${DIM}  ⟳ Network error (curl exit $curl_exit_code), retry $attempt/$HTTP_MAX_ATTEMPTS in ${backoff}s${NC}" >&2
                sleep "$backoff"
                continue
            fi
            HTTP_CODE="000"
            return 1
        fi
        
        if [[ "$HTTP_CODE" =~ ^2[0-9][0-9]$ ]]; then
            return 0
        fi
        
        if [[ "$HTTP_CODE" =~ ^4[0-9][0-9]$ ]] && [ "$retry_on_4xx" = false ]; then
            if ! is_retryable_code "$HTTP_CODE"; then
                return 0
            fi
        fi
        
        if [ "$no_retry" = true ] || ! is_retryable_code "$HTTP_CODE"; then
            return 0
        fi
        
        ((attempt++)) || true
        if [ $attempt -ge $HTTP_MAX_ATTEMPTS ]; then
            return 0
        fi
        
        local delay=""
        if [ "$HTTP_CODE" == "429" ] || [ "$HTTP_CODE" == "503" ]; then
            delay=$(parse_retry_after "$HTTP_HEADERS")
        fi
        
        if [ -z "$delay" ]; then
            delay=$(calculate_backoff $attempt)
        fi
        
        delay=$(awk -v d="$delay" -v m="$HTTP_MAX_DELAY" 'BEGIN{printf "%.3f", (d < m) ? d : m}')
        
        [ "$silent" = false ] && echo -e "${DIM}  ⟳ HTTP $HTTP_CODE, retry $attempt/$HTTP_MAX_ATTEMPTS in ${delay}s${NC}" >&2
        sleep "$delay"
    done
    
    return 0
}

http_get() {
    http_request GET "$@"
}

http_get_events() {
    if [ "$NO_CACHE" = true ]; then
        http_request GET "$@" -H "Cache-Control: no-cache"
    else
        http_request GET "$@"
    fi
}

http_post() {
    local url="$1"
    shift
    http_request POST "$url" "$@"
}

SEMANTIC_PASS=0
SEMANTIC_FAIL=1
SEMANTIC_RETRY=2
SEMANTIC_ATTEMPTS=0
SEMANTIC_EXHAUSTED=false

assert_nonempty_array() {
    local body="$1"
    if [ -z "$body" ] || [ "$body" == "[]" ]; then
        return $SEMANTIC_RETRY
    fi
    local length=$(echo "$body" | jq 'if type == "array" then length else -1 end' 2>/dev/null || echo "-1")
    if [ "$length" == "-1" ]; then
        return $SEMANTIC_FAIL
    elif [ "$length" == "0" ]; then
        return $SEMANTIC_RETRY
    fi
    return $SEMANTIC_PASS
}

assert_nonempty_data() {
    local body="$1"
    if [ -z "$body" ]; then
        return $SEMANTIC_RETRY
    fi
    local data_length=$(echo "$body" | jq 'if .data then (.data | length) else -1 end' 2>/dev/null || echo "-1")
    if [ "$data_length" == "0" ]; then
        return $SEMANTIC_RETRY
    elif [ "$data_length" == "-1" ]; then
        return $SEMANTIC_PASS
    fi
    return $SEMANTIC_PASS
}

# http_get_semantic - GET with semantic retry on empty results
# Usage: http_get_semantic URL ASSERTION_FN [http_options...]
# Sets: HTTP_CODE, HTTP_BODY, SEMANTIC_ATTEMPTS, SEMANTIC_EXHAUSTED
http_get_semantic() {
    local url="$1"
    shift
    local assertion="$1"
    shift
    
    local max_attempts=$HTTP_MAX_ATTEMPTS
    local attempt=0
    SEMANTIC_EXHAUSTED=false
    SEMANTIC_ATTEMPTS=0
    
    while [ $attempt -lt $max_attempts ]; do
        ((attempt++)) || true
        SEMANTIC_ATTEMPTS=$attempt
        
        http_get "$url" "$@"
        
        if ! [[ "$HTTP_CODE" =~ ^2[0-9][0-9]$ ]]; then
            return 0
        fi
        
        local result=0
        $assertion "$HTTP_BODY" || result=$?
        
        case $result in
            $SEMANTIC_PASS)
                return 0
                ;;
            $SEMANTIC_FAIL)
                return 0
                ;;
            $SEMANTIC_RETRY)
                if [ $attempt -lt $max_attempts ]; then
                    local backoff=$(calculate_backoff $attempt)
                    echo -e "${DIM}  ⟳ Semantic retry $attempt/$max_attempts: empty result, waiting ${backoff}s${NC}" >&2
                    sleep "$backoff"
                fi
                ;;
        esac
    done
    
    SEMANTIC_EXHAUSTED=true
    return 0
}

http_get_events_semantic() {
    local url="$1"
    shift
    local assertion="$1"
    shift
    
    local max_attempts=$HTTP_MAX_ATTEMPTS
    local attempt=0
    SEMANTIC_EXHAUSTED=false
    SEMANTIC_ATTEMPTS=0
    
    while [ $attempt -lt $max_attempts ]; do
        ((attempt++)) || true
        SEMANTIC_ATTEMPTS=$attempt
        
        http_get_events "$url" "$@"
        
        if ! [[ "$HTTP_CODE" =~ ^2[0-9][0-9]$ ]]; then
            return 0
        fi
        
        local result=0
        $assertion "$HTTP_BODY" || result=$?
        
        case $result in
            $SEMANTIC_PASS)
                return 0
                ;;
            $SEMANTIC_FAIL)
                return 0
                ;;
            $SEMANTIC_RETRY)
                if [ $attempt -lt $max_attempts ]; then
                    local backoff=$(calculate_backoff $attempt)
                    echo -e "${DIM}  ⟳ Semantic retry $attempt/$max_attempts: empty result, waiting ${backoff}s${NC}" >&2
                    sleep "$backoff"
                fi
                ;;
        esac
    done
    
    SEMANTIC_EXHAUSTED=true
    return 0
}

check_events_response() {
    local http_code=$1
    local body=$2
    local context=$3
    
    if [ "$http_code" != "200" ]; then
        print_fail "API returned HTTP $http_code"
        print_response "Response: $body"
        return 1
    fi
    
    local is_array=$(echo "$body" | jq -r 'if type == "array" then "yes" else "no" end' 2>/dev/null)
    if [ "$is_array" != '"yes"' ]; then
        local error_msg=$(echo "$body" | jq -r '.error // .message // .detail // empty' 2>/dev/null)
        if [ -n "$error_msg" ]; then
            print_fail "API returned error: $error_msg"
        else
            print_fail "API returned unexpected response type"
        fi
        print_response "Response: $body"
        return 1
    fi
    
    return 0
}

check_status() {
    local actual=$1
    local expected=$2
    local message=$3
    
    if [ "$actual" == "$expected" ]; then
        print_pass "$message (HTTP $actual)"
        return 0
    else
        print_fail "$message - Expected HTTP $expected, got HTTP $actual"
        return 1
    fi
}

check_json_field() {
    local json=$1
    local field=$2
    local expected=$3
    local message=$4
    
    local actual=$(echo "$json" | jq -r "$field" 2>/dev/null)
    
    if [ "$actual" == "$expected" ]; then
        print_pass "$message"
        return 0
    else
        print_fail "$message - Expected '$expected', got '$actual'"
        return 1
    fi
}

format_decimal() {
    local value="$1"
    if [[ "$value" =~ ^-?[0-9]+([.][0-9]+)?$ ]]; then
        printf "%.2f" "$value"
    else
        echo "$value"
    fi
}

check_json_decimal() {
    local json=$1
    local field=$2
    local expected=$3
    local message=$4

    local actual=$(echo "$json" | jq -r "$field" 2>/dev/null)
    local actual_fmt=$(format_decimal "$actual")
    local expected_fmt=$(format_decimal "$expected")

    if [ "$actual_fmt" == "$expected_fmt" ]; then
        print_pass "$message"
        return 0
    else
        print_fail "$message - Expected '$expected_fmt', got '$actual_fmt'"
        return 1
    fi
}

clear
echo ""
echo -e "${BOLD}${WHITE}F1 Bets API - Smoke Test Suite${NC}"
echo -e "${DIM}Testing: $BASE_URL${NC}"
echo ""

# ============================================================================
# PHASE 1: HEALTH AND DOCUMENTATION
# ============================================================================
print_header "PHASE 1: HEALTH AND DOCUMENTATION"

print_section "1.1 Health Endpoint"
print_test "Verify API is running and healthy"
print_expected "HTTP 200 with status=UP"

http_get "$BASE_URL/actuator/health"
if check_status "$HTTP_CODE" "200" "Health endpoint returns HTTP 200"; then
    STATUS=$(echo "$HTTP_BODY" | jq -r '.status' 2>/dev/null)
    if [ "$STATUS" == "UP" ]; then
        print_pass "Service status is UP"
    else
        print_fail "Service status is $STATUS, expected UP"
    fi
fi
print_response "Response: $HTTP_BODY"

print_section "1.2 Swagger UI"
print_test "Verify Swagger UI documentation is accessible"
print_expected "HTTP 200 (following redirects)"

http_get "$BASE_URL/swagger-ui.html" --follow
check_status "$HTTP_CODE" "200" "Swagger UI accessible at /swagger-ui.html"

print_section "1.3 Root/Info Endpoint"
print_test "Verify API info endpoint returns service name and version"
print_expected "JSON with service='F1 Bets API' and version present"

http_get "$BASE_URL/"
SERVICE=$(echo "$HTTP_BODY" | jq -r '.service' 2>/dev/null)
VERSION=$(echo "$HTTP_BODY" | jq -r '.version' 2>/dev/null)

if [ "$SERVICE" == "F1 Bets API" ]; then
    print_pass "Service name is 'F1 Bets API'"
else
    print_fail "Service name is '$SERVICE', expected 'F1 Bets API'"
fi

if [ -n "$VERSION" ] && [ "$VERSION" != "null" ]; then
    print_pass "Version present: $VERSION"
else
    print_fail "Version missing from response"
fi
print_response "Response: $HTTP_BODY"

# ============================================================================
# PHASE 2: EVENTS API
# ============================================================================
print_header "PHASE 2: EVENTS API"
if [ "$NO_CACHE" = true ]; then
    print_info "Using resilient HTTP client with automatic retries (cache disabled)"
else
    print_info "Using resilient HTTP client with automatic retries"
fi

# NOTE: Section 2.1 "List All Events" (unfiltered) is skipped because:
# - OpenF1 API has undocumented rate limits (3 req/sec)
# - Fetching all sessions + drivers for each exceeds the rate limit
# - Use filtered queries instead (sections 2.2-2.6)

# Set default filter values for subsequent tests
FILTER_TYPE="Race"
FILTER_YEAR="2024"
FILTER_COUNTRY="GBR"

print_section "2.1 Filter by Session Type"
print_test "GET /api/v1/events?sessionType=$FILTER_TYPE"
print_expected "HTTP 200 with non-empty results"

http_get_events_semantic "$BASE_URL/api/v1/events?sessionType=$FILTER_TYPE" assert_nonempty_array
if [ "$HTTP_CODE" == "200" ]; then
    COUNT=$(echo "$HTTP_BODY" | jq 'length' 2>/dev/null || echo "0")
    if [ "$COUNT" -gt 0 ]; then
        print_pass "API responded with HTTP 200"
        WRONG=$(echo "$HTTP_BODY" | jq --arg t "$FILTER_TYPE" '[.[] | select(.sessionType != $t)] | length' 2>/dev/null)
        if [ "$WRONG" == "0" ]; then
            print_pass "All $COUNT events have sessionType=$FILTER_TYPE"
        else
            print_fail "Found $WRONG events with wrong sessionType"
        fi
    else
        if [ "$SEMANTIC_EXHAUSTED" = true ]; then
            print_fail "Expected non-empty results, got empty array after $SEMANTIC_ATTEMPTS attempts"
        else
            print_pass "Empty array returned (handled gracefully)"
        fi
    fi
else
    print_fail "API returned HTTP $HTTP_CODE"
fi

print_section "2.2 Filter by Year"
print_test "GET /api/v1/events?year=$FILTER_YEAR"
print_expected "HTTP 200 with non-empty results"

http_get_events_semantic "$BASE_URL/api/v1/events?year=$FILTER_YEAR" assert_nonempty_array
if [ "$HTTP_CODE" == "200" ]; then
    COUNT=$(echo "$HTTP_BODY" | jq 'length' 2>/dev/null || echo "0")
    if [ "$COUNT" -gt 0 ]; then
        print_pass "API responded with HTTP 200"
        WRONG=$(echo "$HTTP_BODY" | jq --argjson y "$FILTER_YEAR" '[.[] | select(.year != $y)] | length' 2>/dev/null)
        if [ "$WRONG" == "0" ]; then
            print_pass "All $COUNT events are from year=$FILTER_YEAR"
        else
            print_fail "Found $WRONG events from wrong year"
        fi
    else
        if [ "$SEMANTIC_EXHAUSTED" = true ]; then
            print_fail "Expected non-empty results, got empty array after $SEMANTIC_ATTEMPTS attempts"
        else
            print_pass "Empty array returned (handled gracefully)"
        fi
    fi
else
    print_fail "API returned HTTP $HTTP_CODE"
fi

print_section "2.3 Filter by Country"
print_test "GET /api/v1/events?countryCode=$FILTER_COUNTRY"
print_expected "HTTP 200 with non-empty results"

http_get_events_semantic "$BASE_URL/api/v1/events?countryCode=$FILTER_COUNTRY" assert_nonempty_array
if [ "$HTTP_CODE" == "200" ]; then
    COUNT=$(echo "$HTTP_BODY" | jq 'length' 2>/dev/null || echo "0")
    if [ "$COUNT" -gt 0 ]; then
        print_pass "API responded with HTTP 200"
        WRONG=$(echo "$HTTP_BODY" | jq --arg c "$FILTER_COUNTRY" '[.[] | select(.countryCode != $c)] | length' 2>/dev/null)
        if [ "$WRONG" == "0" ]; then
            print_pass "All $COUNT events have countryCode=$FILTER_COUNTRY"
        else
            print_fail "Found $WRONG events with wrong countryCode"
        fi
    else
        if [ "$SEMANTIC_EXHAUSTED" = true ]; then
            print_fail "Expected non-empty results, got empty array after $SEMANTIC_ATTEMPTS attempts"
        else
            print_pass "Empty array returned (handled gracefully)"
        fi
    fi
else
    print_fail "API returned HTTP $HTTP_CODE"
fi

print_section "2.4 Combined Filters"
print_test "GET /api/v1/events?year=$FILTER_YEAR&sessionType=$FILTER_TYPE&countryCode=$FILTER_COUNTRY"
print_expected "HTTP 200 with non-empty results"

http_get_events_semantic "$BASE_URL/api/v1/events?year=$FILTER_YEAR&sessionType=$FILTER_TYPE&countryCode=$FILTER_COUNTRY" assert_nonempty_array
if [ "$HTTP_CODE" == "200" ]; then
    COUNT=$(echo "$HTTP_BODY" | jq 'length' 2>/dev/null || echo "0")
    if [ "$COUNT" -gt 0 ]; then
        print_pass "API responded with HTTP 200"
        INVALID=$(echo "$HTTP_BODY" | jq --argjson y "$FILTER_YEAR" --arg t "$FILTER_TYPE" --arg c "$FILTER_COUNTRY" \
            '[.[] | select(.year != $y or .sessionType != $t or .countryCode != $c)] | length' 2>/dev/null)
        if [ "$INVALID" == "0" ]; then
            print_pass "All $COUNT events match all filters"
        else
            print_fail "Found $INVALID events not matching all criteria"
        fi
    else
        if [ "$SEMANTIC_EXHAUSTED" = true ]; then
            print_fail "Expected non-empty results, got empty array after $SEMANTIC_ATTEMPTS attempts"
        else
            print_pass "Empty array returned (handled gracefully)"
        fi
    fi
else
    print_fail "API returned HTTP $HTTP_CODE"
fi

print_section "2.5 Driver Market Array"
print_test "Verify events include driver market with required fields"
print_expected "Each event has drivers array with driverNumber, fullName, and odds (2, 3, or 4)"

# Fetch events with filter for this test (since we skipped the unfiltered fetch)
http_get_events "$BASE_URL/api/v1/events?sessionType=$FILTER_TYPE&year=$FILTER_YEAR"
ALL_EVENTS="$HTTP_BODY"
FIRST_EVENT=$(echo "$ALL_EVENTS" | jq '.[0]' 2>/dev/null)

if [ "$FIRST_EVENT" != "null" ] && [ -n "$FIRST_EVENT" ]; then
    DRIVERS=$(echo "$FIRST_EVENT" | jq '.drivers' 2>/dev/null)
    DRIVER_COUNT=$(echo "$DRIVERS" | jq 'length' 2>/dev/null || echo "0")
    
    if [ "$DRIVER_COUNT" -gt 0 ]; then
        print_pass "Event has $DRIVER_COUNT drivers in market"
        
        FIRST_DRIVER=$(echo "$DRIVERS" | jq '.[0]' 2>/dev/null)
        DRIVER_NUM=$(echo "$FIRST_DRIVER" | jq '.driverNumber' 2>/dev/null)
        FULL_NAME=$(echo "$FIRST_DRIVER" | jq -r '.fullName' 2>/dev/null)
        ODDS=$(echo "$FIRST_DRIVER" | jq '.odds' 2>/dev/null)
        
        if [ "$DRIVER_NUM" != "null" ] && [ -n "$DRIVER_NUM" ]; then
            print_pass "driverNumber present (integer): $DRIVER_NUM"
        else
            print_fail "driverNumber missing or invalid"
        fi
        
        if [ "$FULL_NAME" != "null" ] && [ -n "$FULL_NAME" ]; then
            print_pass "fullName present (string): $FULL_NAME"
        else
            print_fail "fullName missing or invalid"
        fi
        
        if [ "$ODDS" == "2" ] || [ "$ODDS" == "3" ] || [ "$ODDS" == "4" ]; then
            print_pass "odds is valid (2, 3, or 4): $ODDS"
        else
            print_fail "odds is invalid: $ODDS (must be 2, 3, or 4)"
        fi
        
        INVALID_ODDS=$(echo "$DRIVERS" | jq '[.[] | select(.odds < 2 or .odds > 4)] | length' 2>/dev/null)
        if [ "$INVALID_ODDS" == "0" ]; then
            print_pass "All driver odds are in valid range [2, 3, 4]"
        else
            print_fail "Found $INVALID_ODDS drivers with invalid odds"
        fi
        print_response "Sample driver: $(echo "$FIRST_DRIVER" | jq -c '.' 2>/dev/null)"
    else
        print_fail "No drivers in market array"
        print_response "Event: $(echo "$FIRST_EVENT" | jq -c '{sessionKey, sessionType, drivers}' 2>/dev/null)"
    fi
else
    print_fail "No events available to verify driver market"
    print_response "Events response: $(echo "$ALL_EVENTS" | jq -c '.' 2>/dev/null | head -c 200 || true)"
fi

# ============================================================================
# PHASE 3: CORE BETTING FLOW
# ============================================================================
print_header "PHASE 3: CORE BETTING FLOW"

# Find TWO unsettled sessions from the events fetched in Phase 2:
# 1. BETTING_SESSION (SESSION_KEY) - for Phase 3 core flow, gets settled (must be ended)
# 2. TEST_SESSION - for Phases 4-7 tests, stays open (prefers future)
# (Previous test runs may have settled some sessions in the database)
USER_ID="betting-flow-$(uuid | cut -c1-8)"
EVENT_COUNT=$(echo "$ALL_EVENTS" | jq 'length' 2>/dev/null || echo "0")
SESSION_KEY="${SESSION_KEY:-}"
FIRST_DRIVER="${FIRST_DRIVER:-}"
SECOND_DRIVER="${SECOND_DRIVER:-}"
TEST_SESSION="${TEST_SESSION:-}"
TEST_DRIVER="${TEST_DRIVER:-}"
BETTING_PROBE_PLACED=false
SKIP_OPEN_SESSION_TESTS=false
SKIP_SETTLEMENT_TESTS=false
SKIP_BETTING_FLOW=false
NOW_EPOCH=$(date +%s)
CURRENT_YEAR=$(date +%Y)
SETTLE_YEAR="${SETTLE_YEAR:-$((CURRENT_YEAR - 1))}"
SETTLE_LOOKBACK="${SETTLE_LOOKBACK:-3}"
SETTLE_EXTENDED_LOOKBACK="${SETTLE_EXTENDED_LOOKBACK:-2}"
FALLBACK_COUNTRIES="${FALLBACK_COUNTRIES:-USA UAE ITA}"
SETTLE_TRIED_YEARS=""

build_candidates() {
    local events="$1"
    echo "$events" | jq -r --argjson now "$NOW_EPOCH" --argjson current_year "$CURRENT_YEAR" '
        to_entries[] |
        .value as $e |
        (try ($e.dateEnd | fromdateiso8601) catch null) as $end_ts |
        ($e.year // 0) as $yr |
        [
            ($e.sessionKey // ""),
            ($e.drivers[0].driverNumber // ""),
            ($e.drivers[1].driverNumber // ""),
            (
                if $end_ts != null then
                    (if $end_ts <= $now then "ended" else "future" end)
                elif $yr != 0 and $yr < $current_year then
                    "ended"
                elif $yr != 0 and $yr > $current_year then
                    "future"
                else
                    "unknown"
                end
            )
        ] | @tsv
    ' 2>/dev/null || echo ""
}

OPEN_CANDIDATES=$(build_candidates "$ALL_EVENTS")
SETTLE_EVENTS="$ALL_EVENTS"
SETTLE_CANDIDATES="$OPEN_CANDIDATES"

find_ended_unsettled_session() {
    local candidates="$1"
    if [ -n "$SESSION_KEY" ]; then
        return 0
    fi
    ENDED_CANDIDATES=0
    SETTLED_CANDIDATES=0
    ALL_ENDED_SETTLED=false
    while IFS=$'\t' read -r CANDIDATE_KEY CANDIDATE_DRIVER CANDIDATE_DRIVER2 CANDIDATE_STATUS; do
        if [ "$CANDIDATE_STATUS" != "ended" ]; then
            continue
        fi
        if [ -z "$CANDIDATE_KEY" ] || [ -z "$CANDIDATE_DRIVER" ] || [ -z "$CANDIDATE_DRIVER2" ]; then
            continue
        fi
        ENDED_CANDIDATES=$((ENDED_CANDIDATES + 1))

        TEST_IDEM=$(uuid)
        http_post "$BASE_URL/api/v1/bets" \
            -H "Content-Type: application/json" \
            -H "X-User-Id: probe-$TEST_IDEM" \
            -H "Idempotency-Key: $TEST_IDEM" \
            -d "{\"sessionKey\": $CANDIDATE_KEY, \"driverNumber\": $CANDIDATE_DRIVER, \"amount\": 1.00}"

        if [ "$HTTP_CODE" = "201" ]; then
            SESSION_KEY="$CANDIDATE_KEY"
            FIRST_DRIVER="$CANDIDATE_DRIVER"
            SECOND_DRIVER="$CANDIDATE_DRIVER2"
            BETTING_PROBE_PLACED=true
            print_info "Betting session: $SESSION_KEY (drivers: #$FIRST_DRIVER, #$SECOND_DRIVER)"
            return 0
        elif [ "$HTTP_CODE" = "409" ]; then
            SETTLED_CANDIDATES=$((SETTLED_CANDIDATES + 1))
            print_info "Session $CANDIDATE_KEY already settled, skipping..."
        else
            if [ "${DEBUG_CURL:-false}" = true ]; then
                print_warning "Probe bet for session $CANDIDATE_KEY returned HTTP $HTTP_CODE"
                if [ -n "$HTTP_BODY" ]; then
                    print_response "Response: $HTTP_BODY"
                fi
            fi
        fi
    done <<< "$candidates"
    if [ "$ENDED_CANDIDATES" -gt 0 ] && [ "$SETTLED_CANDIDATES" -eq "$ENDED_CANDIDATES" ]; then
        ALL_ENDED_SETTLED=true
    fi
    return 1
}

find_unsettled_session() {
    local candidates="$1"
    if [ -n "$SESSION_KEY" ]; then
        return 0
    fi
    while IFS=$'\t' read -r CANDIDATE_KEY CANDIDATE_DRIVER CANDIDATE_DRIVER2 CANDIDATE_STATUS; do
        if [ -z "$CANDIDATE_KEY" ] || [ -z "$CANDIDATE_DRIVER" ] || [ -z "$CANDIDATE_DRIVER2" ]; then
            continue
        fi

        TEST_IDEM=$(uuid)
        http_post "$BASE_URL/api/v1/bets" \
            -H "Content-Type: application/json" \
            -H "X-User-Id: probe-$TEST_IDEM" \
            -H "Idempotency-Key: $TEST_IDEM" \
            -d "{\"sessionKey\": $CANDIDATE_KEY, \"driverNumber\": $CANDIDATE_DRIVER, \"amount\": 1.00}"

        if [ "$HTTP_CODE" = "201" ]; then
            SESSION_KEY="$CANDIDATE_KEY"
            FIRST_DRIVER="$CANDIDATE_DRIVER"
            SECOND_DRIVER="$CANDIDATE_DRIVER2"
            BETTING_PROBE_PLACED=true
            print_info "Betting session: $SESSION_KEY (drivers: #$FIRST_DRIVER, #$SECOND_DRIVER, status: $CANDIDATE_STATUS)"
            return 0
        elif [ "$HTTP_CODE" = "409" ]; then
            print_info "Session $CANDIDATE_KEY already settled, skipping..."
        else
            if [ "${DEBUG_CURL:-false}" = true ]; then
                print_warning "Probe bet for session $CANDIDATE_KEY returned HTTP $HTTP_CODE"
                if [ -n "$HTTP_BODY" ]; then
                    print_response "Response: $HTTP_BODY"
                fi
            fi
        fi
    done <<< "$candidates"
    return 1
}

search_settlement_year() {
    local year="$1"
    local countries="$2"

    http_get_events "$BASE_URL/api/v1/events?sessionType=$FILTER_TYPE&year=$year"
    if [ "$HTTP_CODE" = "200" ]; then
        SETTLE_EVENTS="$HTTP_BODY"
        SETTLE_CANDIDATES=$(build_candidates "$SETTLE_EVENTS")
        SETTLE_EVENT_COUNT=$(echo "$SETTLE_EVENTS" | jq 'length' 2>/dev/null || echo "0")
        print_info "Searching for ended, unsettled session among $SETTLE_EVENT_COUNT events from $year..."
        find_ended_unsettled_session "$SETTLE_CANDIDATES" || true
        if [ -n "$SESSION_KEY" ]; then
            return 0
        fi
        if [ "$SETTLE_EVENT_COUNT" -eq 0 ] || [ "$ALL_ENDED_SETTLED" = true ]; then
            for c in $countries; do
                http_get_events "$BASE_URL/api/v1/events?sessionType=$FILTER_TYPE&year=$year&countryCode=$c"
                if [ "$HTTP_CODE" = "200" ]; then
                    SETTLE_EVENTS="$HTTP_BODY"
                    SETTLE_CANDIDATES=$(build_candidates "$SETTLE_EVENTS")
                    SETTLE_EVENT_COUNT=$(echo "$SETTLE_EVENTS" | jq 'length' 2>/dev/null || echo "0")
                    print_info "Searching for ended, unsettled session among $SETTLE_EVENT_COUNT events from $year ($c)..."
                    find_ended_unsettled_session "$SETTLE_CANDIDATES" || true
                    if [ -n "$SESSION_KEY" ]; then
                        return 0
                    fi
                fi
            done
        fi
    else
        print_warning "Fallback year query failed (HTTP $HTTP_CODE)"
    fi
    return 1
}

print_info "Test user: $USER_ID"
print_info "Searching for sessions among $EVENT_COUNT events..."

if [ -n "$SESSION_KEY" ]; then
    if [ -z "$FIRST_DRIVER" ] || [ -z "$SECOND_DRIVER" ]; then
        FIRST_DRIVER=$(echo "$ALL_EVENTS" | jq -r --arg key "$SESSION_KEY" \
            '[.[] | select((.sessionKey|tostring) == $key)][0].drivers[0].driverNumber // empty' 2>/dev/null)
        SECOND_DRIVER=$(echo "$ALL_EVENTS" | jq -r --arg key "$SESSION_KEY" \
            '[.[] | select((.sessionKey|tostring) == $key)][0].drivers[1].driverNumber // empty' 2>/dev/null)
    fi

    if [ -z "$FIRST_DRIVER" ] || [ -z "$SECOND_DRIVER" ]; then
        print_fail "SESSION_KEY override requires FIRST_DRIVER and SECOND_DRIVER, or the session must be present in events"
        exit 1
    fi

    SESSION_STATUS=$(echo "$SETTLE_CANDIDATES" | awk -F'\t' -v key="$SESSION_KEY" '$1==key {print $4; found=1} END {if (!found) print ""}')
    if [ -n "$SESSION_STATUS" ] && [ "$SESSION_STATUS" != "ended" ]; then
        print_warning "SESSION_KEY $SESSION_KEY is $SESSION_STATUS; settlement may fail if event hasn't ended"
    fi
else
    print_info "Searching for ended, unsettled session for settlement..."
    find_ended_unsettled_session "$SETTLE_CANDIDATES" || true

    if [ -z "$SESSION_KEY" ]; then
        if [ "$ALL_ENDED_SETTLED" = true ]; then
            print_info "All ended sessions in current events are already settled; switching to extended search"
        else
            for i in $(seq 0 "$SETTLE_LOOKBACK"); do
                SETTLE_YEAR_CANDIDATE=$((SETTLE_YEAR - i))
                SETTLE_TRIED_YEARS="${SETTLE_TRIED_YEARS}${SETTLE_TRIED_YEARS:+, }$SETTLE_YEAR_CANDIDATE"
                print_info "No ended session found in current events; trying year $SETTLE_YEAR_CANDIDATE..."
                search_settlement_year "$SETTLE_YEAR_CANDIDATE" "$FALLBACK_COUNTRIES" || true
                if [ -n "$SESSION_KEY" ]; then
                    break
                fi
            done
        fi
    fi

    if [ -z "$SESSION_KEY" ] && [ "$SETTLE_EXTENDED_LOOKBACK" -gt 0 ]; then
        EXTENDED_START=$((SETTLE_LOOKBACK + 1))
        EXTENDED_END=$((SETTLE_LOOKBACK + SETTLE_EXTENDED_LOOKBACK))
        print_info "Extended settlement search: years $((SETTLE_YEAR - EXTENDED_START))..$((SETTLE_YEAR - EXTENDED_END))"
        for i in $(seq "$EXTENDED_START" "$EXTENDED_END"); do
            SETTLE_YEAR_CANDIDATE=$((SETTLE_YEAR - i))
            SETTLE_TRIED_YEARS="${SETTLE_TRIED_YEARS}${SETTLE_TRIED_YEARS:+, }$SETTLE_YEAR_CANDIDATE"
            search_settlement_year "$SETTLE_YEAR_CANDIDATE" "$FALLBACK_COUNTRIES" || true
            if [ -n "$SESSION_KEY" ]; then
                break
            fi
        done
    fi
fi

if [ -z "$SESSION_KEY" ]; then
    find_unsettled_session "$OPEN_CANDIDATES" || true
    if [ -z "$SESSION_KEY" ] && [ "$SETTLE_CANDIDATES" != "$OPEN_CANDIDATES" ]; then
        find_unsettled_session "$SETTLE_CANDIDATES" || true
    fi

    if [ -n "$SESSION_KEY" ]; then
        SKIP_SETTLEMENT_TESTS=true
        print_warning "No ended, unsettled sessions available; using session $SESSION_KEY for bet placement only"
    else
        SKIP_BETTING_FLOW=true
        SKIP_OPEN_SESSION_TESTS=true
        SKIP_SETTLEMENT_TESTS=true
        if [ -n "$SETTLE_TRIED_YEARS" ]; then
            print_warning "No sessions available for betting (years tried: $SETTLE_TRIED_YEARS); skipping Phase 3"
        else
            print_warning "No sessions available for betting; skipping Phase 3"
        fi
        print_info "To re-enable full coverage, restart the database: docker compose down -v && docker compose up -d"
    fi
fi

if [ "$SKIP_BETTING_FLOW" = true ]; then
    print_warning "Skipping Phase 3 - no sessions available for betting"
else
if [ -n "$TEST_SESSION" ]; then
    if [ -z "$TEST_DRIVER" ]; then
        TEST_DRIVER=$(echo "$ALL_EVENTS" | jq -r --arg key "$TEST_SESSION" \
            '[.[] | select((.sessionKey|tostring) == $key)][0].drivers[0].driverNumber // empty' 2>/dev/null)
    fi

    if [ -z "$TEST_DRIVER" ]; then
        print_warning "TEST_SESSION $TEST_SESSION has no drivers in events list; skipping Phases 4-7"
        SKIP_OPEN_SESSION_TESTS=true
    fi
fi

if [ -z "$TEST_SESSION" ] && [ "$SKIP_OPEN_SESSION_TESTS" = false ]; then
    print_info "Searching for open session for Phases 4-7 (prefer future)..."
    for DESIRED_STATUS in future unknown ended; do
        while IFS=$'\t' read -r CANDIDATE_KEY CANDIDATE_DRIVER _ CANDIDATE_STATUS; do
            if [ "$CANDIDATE_STATUS" != "$DESIRED_STATUS" ]; then
                continue
            fi
            if [ -z "$CANDIDATE_KEY" ] || [ -z "$CANDIDATE_DRIVER" ]; then
                continue
            fi
            if [ "$CANDIDATE_KEY" = "$SESSION_KEY" ]; then
                continue
            fi

            TEST_IDEM=$(uuid)
            http_post "$BASE_URL/api/v1/bets" --no-retry \
                -H "Content-Type: application/json" \
                -H "X-User-Id: probe-$TEST_IDEM" \
                -H "Idempotency-Key: $TEST_IDEM" \
                -d "{\"sessionKey\": $CANDIDATE_KEY, \"driverNumber\": $CANDIDATE_DRIVER, \"amount\": 1.00}"

            if [ "$HTTP_CODE" = "201" ]; then
                TEST_SESSION="$CANDIDATE_KEY"
                TEST_DRIVER="$CANDIDATE_DRIVER"
                print_info "Test session: $TEST_SESSION (driver: #$TEST_DRIVER, status: $DESIRED_STATUS)"
                break 2
            elif [ "$HTTP_CODE" = "409" ]; then
                print_info "Session $CANDIDATE_KEY already settled, skipping..."
            fi
        done <<< "$OPEN_CANDIDATES"
    done
fi

if [ -z "$TEST_SESSION" ] || [ "$TEST_SESSION" = "$SESSION_KEY" ]; then
    SKIP_OPEN_SESSION_TESTS=true
    if [ -z "$TEST_SESSION" ]; then
        print_warning "No separate open session found; skipping Phases 4-7"
    else
        print_warning "TEST_SESSION equals SESSION_KEY; skipping Phases 4-7 (session will be settled)"
    fi
fi

print_info "Using betting session $SESSION_KEY with drivers #$FIRST_DRIVER (winner) and #$SECOND_DRIVER (loser)"
if [ "$SKIP_SETTLEMENT" = true ]; then
    SKIP_SETTLEMENT_TESTS=true
    print_warning "Settlement tests disabled via SKIP_SETTLEMENT=true"
fi
if [ "$SKIP_SETTLEMENT_TESTS" = true ]; then
    print_warning "Settlement-dependent checks will be skipped (no ended, unsettled sessions available)"
fi
if [ "$SKIP_OPEN_SESSION_TESTS" = false ]; then
    print_info "Using test session $TEST_SESSION with driver #$TEST_DRIVER"
fi

EXPECTED_MIN_TOTAL=0
EXPECTED_MIN_WIN=0
if [ "$BETTING_PROBE_PLACED" = true ]; then
    EXPECTED_MIN_TOTAL=$((EXPECTED_MIN_TOTAL + 1))
    EXPECTED_MIN_WIN=$((EXPECTED_MIN_WIN + 1))
fi

print_section "3.1 New User Starting Balance"
print_test "New user places first bet and receives EUR 100.00 starting balance automatically"
print_expected "HTTP 201, userBalance shows deduction from 100.00"

IDEM_KEY_1=$(uuid)
http_post "$BASE_URL/api/v1/bets" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER_ID" \
    -H "Idempotency-Key: $IDEM_KEY_1" \
    -d "{\"sessionKey\": $SESSION_KEY, \"driverNumber\": $FIRST_DRIVER, \"amount\": 25.00}"

if check_status "$HTTP_CODE" "201" "Bet created successfully"; then
    BET_ID_1=$(echo "$HTTP_BODY" | jq -r '.betId')
    BALANCE=$(echo "$HTTP_BODY" | jq -r '.userBalance')
    ODDS_1=$(echo "$HTTP_BODY" | jq -r '.odds')
    EXPECTED_MIN_TOTAL=$((EXPECTED_MIN_TOTAL + 1))
    EXPECTED_MIN_WIN=$((EXPECTED_MIN_WIN + 1))
    
    print_pass "Bet ID: $BET_ID_1"
    check_json_decimal "$HTTP_BODY" ".userBalance" "75.00" "Balance deducted correctly (100.00 - 25.00 = 75.00)"
    check_json_field "$HTTP_BODY" ".status" "PENDING" "Bet status is PENDING"
    print_money "Stake: EUR 25.00 × Odds $ODDS_1 = Potential EUR $(echo "$HTTP_BODY" | jq -r '.potentialWinnings')"
fi
print_response "Response: $(echo "$HTTP_BODY" | jq -c '.')"

print_section "3.2 Verify Balance After Bet"
print_test "GET user profile shows correct balance after bet"
print_expected "Balance = 75.00, betCount = 1"

http_get "$BASE_URL/api/v1/users/$USER_ID"
check_json_decimal "$HTTP_BODY" ".balance" "75.00" "User balance is EUR 75.00"
BET_COUNT=$(echo "$HTTP_BODY" | jq '.bets | length')
if [ "$BET_COUNT" == "1" ]; then
    print_pass "User has 1 bet on record"
else
    print_fail "Expected 1 bet, found $BET_COUNT"
fi

print_section "3.3 Place Second Bet on Different Driver"
print_test "Place bet on driver #$SECOND_DRIVER (losing driver)"
print_expected "HTTP 201, balance reduced to 45.00"

http_post "$BASE_URL/api/v1/bets" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER_ID" \
    -H "Idempotency-Key: $(uuid)" \
    -d "{\"sessionKey\": $SESSION_KEY, \"driverNumber\": $SECOND_DRIVER, \"amount\": 30.00}"

if check_status "$HTTP_CODE" "201" "Second bet created"; then
    BET_ID_2=$(echo "$HTTP_BODY" | jq -r '.betId')
    check_json_decimal "$HTTP_BODY" ".userBalance" "45.00" "Balance is now EUR 45.00 (75.00 - 30.00)"
    print_info "Bet on driver #$SECOND_DRIVER with odds $(echo "$HTTP_BODY" | jq -r '.odds')"
    EXPECTED_MIN_TOTAL=$((EXPECTED_MIN_TOTAL + 1))
fi

if [ "$SKIP_SETTLEMENT_TESTS" = true ]; then
    print_section "3.4 Settle Event - Driver #$FIRST_DRIVER Wins"
    print_warning "Skipping 3.4 - no ended, unsettled sessions available"

    print_section "3.5 Verify Winner Gets Payout"
    print_warning "Skipping 3.5 - no ended, unsettled sessions available"

    print_section "3.6 Verify Final Balance Calculation"
    print_warning "Skipping 3.6 - no ended, unsettled sessions available"
else
    print_section "3.4 Settle Event - Driver #$FIRST_DRIVER Wins"
    print_test "Settle race with driver #$FIRST_DRIVER as winner"
    print_expected "HTTP 200, winner matches and counts meet minimums"

    http_post "$BASE_URL/api/v1/events/$SESSION_KEY/settle" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"winningDriverNumber\": $FIRST_DRIVER}"

    if check_status "$HTTP_CODE" "200" "Event settled successfully"; then
        WINNING_DRIVER=$(echo "$HTTP_BODY" | jq -r '.winningDriverNumber' 2>/dev/null)
        TOTAL_BETS=$(echo "$HTTP_BODY" | jq -r '.totalBets' 2>/dev/null)
        WINNING_BETS=$(echo "$HTTP_BODY" | jq -r '.winningBets' 2>/dev/null)

        if [ "$WINNING_DRIVER" == "$FIRST_DRIVER" ]; then
            print_pass "Winning driver matches #$FIRST_DRIVER"
        else
            print_fail "Winning driver mismatch: expected #$FIRST_DRIVER, got #$WINNING_DRIVER"
        fi

        if [[ "$TOTAL_BETS" =~ ^[0-9]+$ ]]; then
            if [ "$TOTAL_BETS" -ge "$EXPECTED_MIN_TOTAL" ]; then
                print_pass "Total bets >= $EXPECTED_MIN_TOTAL (actual $TOTAL_BETS)"
            else
                print_fail "Total bets $TOTAL_BETS < expected minimum $EXPECTED_MIN_TOTAL"
            fi
        else
            print_fail "totalBets missing or invalid: $TOTAL_BETS"
        fi

        if [[ "$WINNING_BETS" =~ ^[0-9]+$ ]]; then
            if [ "$WINNING_BETS" -ge "$EXPECTED_MIN_WIN" ]; then
                print_pass "Winning bets >= $EXPECTED_MIN_WIN (actual $WINNING_BETS)"
            else
                print_fail "Winning bets $WINNING_BETS < expected minimum $EXPECTED_MIN_WIN"
            fi

            if [[ "$TOTAL_BETS" =~ ^[0-9]+$ ]] && [ "$WINNING_BETS" -le "$TOTAL_BETS" ]; then
                print_pass "Winning bets <= total bets"
            elif [[ "$TOTAL_BETS" =~ ^[0-9]+$ ]]; then
                print_fail "Winning bets $WINNING_BETS > total bets $TOTAL_BETS"
            fi
        else
            print_fail "winningBets missing or invalid: $WINNING_BETS"
        fi

        PAYOUT=$(echo "$HTTP_BODY" | jq -r '.totalPayout')
        print_money "Total payout: EUR $PAYOUT"
    fi
    print_response "Response: $(echo "$HTTP_BODY" | jq -c '.')"

    print_section "3.5 Verify Winner Gets Payout"
    print_test "Winning bet (driver #$FIRST_DRIVER) credited stake × odds to balance"
    print_expected "Bet on #$FIRST_DRIVER status=WON, bet on #$SECOND_DRIVER status=LOST"

    http_get "$BASE_URL/api/v1/users/$USER_ID"
    FINAL_BALANCE_RAW=$(echo "$HTTP_BODY" | jq -r '.balance')
    FINAL_BALANCE=$(format_decimal "$FINAL_BALANCE_RAW")

    WON_BETS=$(echo "$HTTP_BODY" | jq '[.bets[] | select(.status=="WON")]')
    LOST_BETS=$(echo "$HTTP_BODY" | jq '[.bets[] | select(.status=="LOST")]')
    WON_COUNT=$(echo "$WON_BETS" | jq 'length')
    LOST_COUNT=$(echo "$LOST_BETS" | jq 'length')

    if [ "$WON_COUNT" == "1" ]; then
        print_pass "1 bet marked as WON"
        WON_DRIVER=$(echo "$WON_BETS" | jq -r '.[0].driverNumber')
        if [ "$WON_DRIVER" == "$FIRST_DRIVER" ]; then
            print_pass "Winning bet was on driver #$FIRST_DRIVER (correct winner)"
        else
            print_fail "Wrong bet marked as won: driver #$WON_DRIVER (expected #$FIRST_DRIVER)"
        fi
    else
        print_fail "Expected 1 WON bet, found $WON_COUNT"
    fi

    if [ "$LOST_COUNT" == "1" ]; then
        print_pass "1 bet marked as LOST"
        LOST_DRIVER=$(echo "$LOST_BETS" | jq -r '.[0].driverNumber')
        if [ "$LOST_DRIVER" == "$SECOND_DRIVER" ]; then
            print_pass "Losing bet was on driver #$SECOND_DRIVER (correct loser)"
        else
            print_fail "Wrong bet marked as lost: driver #$LOST_DRIVER (expected #$SECOND_DRIVER)"
        fi
    else
        print_fail "Expected 1 LOST bet, found $LOST_COUNT"
    fi

    print_section "3.6 Verify Final Balance Calculation"
    print_test "Final balance = 100 - 25 - 30 + (25 × odds)"
    print_expected "Balance reflects winnings credited"

    EXPECTED_RAW=$(echo "100 - 25 - 30 + (25 * $ODDS_1)" | bc)
    EXPECTED_BALANCE=$(printf "%.2f" "$EXPECTED_RAW")
    if [ "$FINAL_BALANCE" == "$EXPECTED_BALANCE" ]; then
        print_pass "Final balance EUR $FINAL_BALANCE matches expected EUR $EXPECTED_BALANCE"
    else
        print_fail "Final balance EUR $FINAL_BALANCE != expected EUR $EXPECTED_BALANCE"
    fi
    print_info "Calculation: 100 - 25 (bet1) - 30 (bet2) + (25 × $ODDS_1 winnings) = EUR $FINAL_BALANCE"
fi
fi

# ============================================================================
# PHASE 4: SERVER-SIDE ODDS ENFORCEMENT
# ============================================================================
print_header "PHASE 4: SERVER-SIDE ODDS ENFORCEMENT"
if [ "$SKIP_OPEN_SESSION_TESTS" = true ]; then
    print_warning "Skipping Phase 4 - no separate open test session available"
else
    print_security "Security: Odds are computed server-side to prevent client manipulation"

    print_section "4.1 Client-Submitted Odds Ignored"
    print_test "Sending odds in request body should be ignored"
    print_expected "Response odds determined by server, not client input"

    USER_ODDS="odds-ignore-$(uuid | cut -c1-8)"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_ODDS" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.00, \"odds\": 999}"

    SERVER_ODDS=$(echo "$HTTP_BODY" | jq -r '.odds')

    if [ "$SERVER_ODDS" == "2" ] || [ "$SERVER_ODDS" == "3" ] || [ "$SERVER_ODDS" == "4" ]; then
        print_pass "Server ignored client odds (999), returned valid odds: $SERVER_ODDS"
    else
        print_fail "Unexpected odds value: $SERVER_ODDS (session: $TEST_SESSION, driver: $TEST_DRIVER)"
    fi

    print_section "4.2 Odds Are Deterministic"
    print_test "Same session + driver combination always returns same odds"
    print_expected "Two different users betting on same session/driver get identical odds"

    USER_DET1="deterministic-$(uuid | cut -c1-8)"
    USER_DET2="deterministic-$(uuid | cut -c1-8)"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_DET1" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.00}"
    ODDS_DET1=$(echo "$HTTP_BODY" | jq -r '.odds')

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_DET2" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.00}"
    ODDS_DET2=$(echo "$HTTP_BODY" | jq -r '.odds')

    if [ "$ODDS_DET1" == "$ODDS_DET2" ]; then
        print_pass "Odds are deterministic: session $TEST_SESSION + driver $TEST_DRIVER = odds $ODDS_DET1"
    else
        print_fail "Odds mismatch: user1=$ODDS_DET1, user2=$ODDS_DET2"
    fi

    print_section "4.3 Odds Range Validation"
    print_test "Verify odds are always 2, 3, or 4 across multiple drivers"
    print_expected "All odds values in [2, 3, 4] (sample size: $ODDS_SAMPLE_SIZE)"

    if ! [[ "$ODDS_SAMPLE_SIZE" =~ ^[0-9]+$ ]] || [ "$ODDS_SAMPLE_SIZE" -le 0 ]; then
        ODDS_SAMPLE_SIZE=5
    fi

    # Use event payload to avoid additional bets and long waits.
    ODDS_SESSION_KEY="$TEST_SESSION"
    if [ -z "$ODDS_SESSION_KEY" ]; then
        ODDS_SESSION_KEY="$SESSION_KEY"
    fi
    print_info "Sampling odds for up to $ODDS_SAMPLE_SIZE drivers from session $ODDS_SESSION_KEY"
    TEST_SESSION_DRIVERS=$(echo "$ALL_EVENTS" | jq -r --arg key "$ODDS_SESSION_KEY" --argjson limit "$ODDS_SAMPLE_SIZE" \
        "[.[] | select((.sessionKey|tostring) == $key) | .drivers[] | {driverNumber, odds}] | .[0:$limit] | .[] | \"\\(.driverNumber)\\t\\(.odds)\"" 2>/dev/null || echo "")
    VALID_ODDS=true
    TESTED_DRIVERS=""

    if [ -z "$TEST_SESSION_DRIVERS" ] && [ "$ODDS_SESSION_KEY" != "$SESSION_KEY" ]; then
        ODDS_SESSION_KEY="$SESSION_KEY"
        print_info "Retrying odds sampling with session $ODDS_SESSION_KEY"
        TEST_SESSION_DRIVERS=$(echo "$ALL_EVENTS" | jq -r --arg key "$ODDS_SESSION_KEY" --argjson limit "$ODDS_SAMPLE_SIZE" \
            "[.[] | select((.sessionKey|tostring) == $key) | .drivers[] | {driverNumber, odds}] | .[0:$limit] | .[] | \"\\(.driverNumber)\\t\\(.odds)\"" 2>/dev/null || echo "")
    fi

    if [ -z "$TEST_SESSION_DRIVERS" ]; then
        print_warning "No drivers found for session $ODDS_SESSION_KEY; skipping odds range validation"
    else
        TEST_SESSION_DRIVER_COUNT=$(printf "%s\n" "$TEST_SESSION_DRIVERS" | awk 'END{print NR}')
        DRIVER_INDEX=0
        while IFS=$'\t' read -r DRIVER_NUM ODDS_RANGE; do
            if [ -z "$DRIVER_NUM" ] || [ "$DRIVER_NUM" = "null" ]; then
                continue
            fi
            DRIVER_INDEX=$((DRIVER_INDEX + 1))
            print_info "Checking odds for driver #$DRIVER_NUM ($DRIVER_INDEX/$TEST_SESSION_DRIVER_COUNT)"
            if [ "$ODDS_RANGE" != "2" ] && [ "$ODDS_RANGE" != "3" ] && [ "$ODDS_RANGE" != "4" ]; then
                print_fail "Driver #$DRIVER_NUM has invalid odds: $ODDS_RANGE"
                VALID_ODDS=false
            fi
            TESTED_DRIVERS="$TESTED_DRIVERS $DRIVER_NUM"
        done <<< "$TEST_SESSION_DRIVERS"

        if [ "$VALID_ODDS" = true ]; then
            print_pass "All tested drivers ($TESTED_DRIVERS) have odds in valid range [2,3,4]"
        fi
    fi
fi

# ============================================================================
# PHASE 5: IDEMPOTENCY PROTECTION
# ============================================================================
print_header "PHASE 5: IDEMPOTENCY PROTECTION"
if [ "$SKIP_OPEN_SESSION_TESTS" = true ]; then
    print_warning "Skipping Phase 5 - no separate open test session available"
else
    print_security "Security: Prevents duplicate charges from network retries and replay attacks"

    USER_IDEM="idempotency-$(uuid | cut -c1-8)"
    IDEM_KEY=$(uuid)
    print_info "Test user: $USER_IDEM"
    print_info "Idempotency-Key: $IDEM_KEY"

    print_section "5.1 Same Key + Same Body Returns Identical Response"
    print_test "Sending identical request twice with same Idempotency-Key"
    print_expected "Both requests return same betId (no duplicate created)"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_IDEM" \
        -H "Idempotency-Key: $IDEM_KEY" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 20.00}"
    BET_ID_REQ1=$(echo "$HTTP_BODY" | jq -r '.betId')

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_IDEM" \
        -H "Idempotency-Key: $IDEM_KEY" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 20.00}"
    BET_ID_REQ2=$(echo "$HTTP_BODY" | jq -r '.betId')

    if [ "$BET_ID_REQ1" == "$BET_ID_REQ2" ]; then
        print_pass "Same betId returned: $BET_ID_REQ1"
        print_pass "No duplicate bet created - idempotency working"
    else
        print_fail "Different betIds: $BET_ID_REQ1 vs $BET_ID_REQ2"
    fi

    print_section "5.2 Balance Only Deducted Once"
    print_test "Check user balance after idempotent replay"
    print_expected "Balance = 80.00 (100 - 20, not 100 - 40)"

    http_get "$BASE_URL/api/v1/users/$USER_IDEM"
    check_json_decimal "$HTTP_BODY" ".balance" "80.00" "Balance only deducted once (100 - 20 = 80)"

    print_section "5.3 Same Key + Different Body Returns 409 Conflict"
    print_test "Reusing Idempotency-Key with different request body"
    print_expected "HTTP 409 Conflict"
    print_security "Security: Prevents request tampering where attacker modifies cached request"

    http_post "$BASE_URL/api/v1/bets" --no-retry \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_IDEM" \
        -H "Idempotency-Key: $IDEM_KEY" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 50.00}"

    check_status "$HTTP_CODE" "409" "Conflict detected - different body with same key rejected"
    print_response "Response: $HTTP_BODY"
fi

# ============================================================================
# PHASE 6: INPUT VALIDATION
# ============================================================================
print_header "PHASE 6: INPUT VALIDATION"
if [ "$SKIP_OPEN_SESSION_TESTS" = true ]; then
    print_warning "Skipping Phase 6 - no separate open test session available"
else
    print_security "Security: Validates all inputs to prevent malformed requests and attacks"

    USER_VAL="validation-$(uuid | cut -c1-8)"

    print_section "6.1 Negative Stake Amount"
    print_test "Attempt bet with negative amount (-50.00)"
    print_expected "HTTP 400 Bad Request"
    print_security "Security: Prevents balance manipulation by adding negative stakes"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": -50.00}"
    check_status "$HTTP_CODE" "400" "Negative amount rejected"

    print_section "6.2 Zero Stake Amount"
    print_test "Attempt bet with zero amount"
    print_expected "HTTP 400 Bad Request"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 0}"
    check_status "$HTTP_CODE" "400" "Zero amount rejected"

    print_section "6.3 Stake Exceeding EUR 10,000 Limit"
    print_test "Attempt bet exceeding maximum stake (15,000.00)"
    print_expected "HTTP 400 Bad Request"
    print_security "Security: Enforces maximum stake limit for risk management"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 15000.00}"
    check_status "$HTTP_CODE" "400" "Amount exceeding 10000 limit rejected"

    print_section "6.4 Invalid Driver Number (>99)"
    print_test "Attempt bet on driver #100 (max valid is 99)"
    print_expected "HTTP 400 Bad Request"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": 100, \"amount\": 10.00}"
    check_status "$HTTP_CODE" "400" "Driver number >99 rejected"

    print_section "6.5 Invalid Driver Number (Negative)"
    print_test "Attempt bet on driver #-1"
    print_expected "HTTP 400 Bad Request"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": -1, \"amount\": 10.00}"
    check_status "$HTTP_CODE" "400" "Negative driver number rejected"

    print_section "6.6 Special Characters in User ID"
    print_test "Attempt with XSS payload in user ID"
    print_expected "HTTP 400 Bad Request"
    print_security "Security: Prevents injection attacks via user ID header"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: user<script>alert(1)</script>" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.00}"
    check_status "$HTTP_CODE" "400" "Special characters in user ID rejected"

    print_section "6.7 Excessive Decimal Precision"
    print_test "Attempt bet with 3+ decimal places (10.001)"
    print_expected "HTTP 400 Bad Request"
    print_security "Security: Prevents rounding exploitation attacks"

    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_VAL" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.001}"
    check_status "$HTTP_CODE" "400" "Excessive decimal precision (3+ places) rejected"
fi

# ============================================================================
# PHASE 7: BUSINESS LOGIC PROTECTION
# ============================================================================
print_header "PHASE 7: BUSINESS LOGIC PROTECTION"

if [ "$SKIP_OPEN_SESSION_TESTS" = true ]; then
    print_section "7.1 Insufficient Balance"
    print_warning "Skipping 7.1 - no separate open test session available"
else
    print_section "7.1 Insufficient Balance"
    print_test "Attempt bet exceeding available balance"
    print_expected "HTTP 402 Payment Required"
    print_security "Security: Prevents overdraft and negative balance scenarios"

    USER_BROKE="broke-$(uuid | cut -c1-8)"

    # First bet uses 95 of 100 starting balance
    http_post "$BASE_URL/api/v1/bets" --silent \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_BROKE" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 95.00}"

    # Second bet for 10 should fail (only 5 remaining)
    http_post "$BASE_URL/api/v1/bets" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_BROKE" \
        -H "Idempotency-Key: $(uuid)" \
        -d "{\"sessionKey\": $TEST_SESSION, \"driverNumber\": $TEST_DRIVER, \"amount\": 10.00}"

    check_status "$HTTP_CODE" "402" "Insufficient balance returns HTTP 402"
    if echo "$HTTP_BODY" | grep -qi "insufficient"; then
        print_pass "Error message mentions insufficient balance"
    fi
fi

print_section "7.2 Double Settlement Prevention"
if [ "$SKIP_SETTLEMENT_TESTS" = true ]; then
    print_warning "Skipping 7.2 - no ended, unsettled sessions available"
else
    print_test "Attempt to settle already-settled event"
    print_expected "HTTP 409 Conflict"
    print_security "Security: Prevents double-payout fraud by re-settling events"

    ALT_DRIVER="$SECOND_DRIVER"
    if [ -z "$ALT_DRIVER" ] || [ "$ALT_DRIVER" = "$FIRST_DRIVER" ]; then
        ALT_DRIVER=$(echo "$ALL_EVENTS" | jq -r --arg key "$SESSION_KEY" --arg first "$FIRST_DRIVER" \
            '[.[] | select((.sessionKey|tostring) == $key) | .drivers[] | select((.driverNumber|tostring) != $first) | .driverNumber][0] // empty' 2>/dev/null)
    fi

    if [ -z "$ALT_DRIVER" ] || [ "$ALT_DRIVER" = "$FIRST_DRIVER" ]; then
        print_warning "No alternate driver found for double-settlement test; skipping 7.2"
    else
        http_post "$BASE_URL/api/v1/events/$SESSION_KEY/settle" --no-retry \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: $(uuid)" \
            -d "{\"winningDriverNumber\": $ALT_DRIVER}"

        check_status "$HTTP_CODE" "409" "Double settlement blocked"
        if echo "$HTTP_BODY" | grep -qi "already"; then
            print_pass "Error message indicates event already settled"
        fi
    fi
fi

print_section "7.3 Non-Existent User Lookup"
print_test "GET user profile for user that doesn't exist"
print_expected "HTTP 404 Not Found"

http_get "$BASE_URL/api/v1/users/nonexistent-user-xyz-99999"
check_status "$HTTP_CODE" "404" "Non-existent user returns HTTP 404"

# ============================================================================
# PHASE 8: RESILIENCE
# ============================================================================
print_header "PHASE 8: RESILIENCE"

print_section "8.1 Events Endpoint Graceful Handling"
print_test "Events endpoint handles OpenF1 unavailability gracefully"
print_expected "Returns HTTP 200 (possibly with cached/empty data) or proper error"

http_get_events "$BASE_URL/api/v1/events?year=2024"
if [ "$HTTP_CODE" == "200" ]; then
    print_pass "Events endpoint responds with HTTP 200"
    EVENT_COUNT=$(echo "$HTTP_BODY" | jq 'length' 2>/dev/null || echo "0")
    if [ "$EVENT_COUNT" -gt 0 ]; then
        print_pass "Returned $EVENT_COUNT events (live or cached)"
    else
        print_pass "Returned empty array (OpenF1 may be unavailable, gracefully handled)"
    fi
elif [ "$HTTP_CODE" == "503" ] || [ "$HTTP_CODE" == "504" ]; then
    print_pass "Returned proper error status $HTTP_CODE (upstream unavailable)"
else
    print_fail "Unexpected response: HTTP $HTTP_CODE"
fi

# ============================================================================
# SUMMARY
# ============================================================================
print_header "TEST SUMMARY"

TOTAL=$((PASS_COUNT + FAIL_COUNT))

echo ""
if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}${BOLD}  ╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}${BOLD}  ║                                                               ║${NC}"
    echo -e "${GREEN}${BOLD}  ║   ✓ ALL TESTS PASSED!                                         ║${NC}"
    echo -e "${GREEN}${BOLD}  ║                                                               ║${NC}"
    echo -e "${GREEN}${BOLD}  ╚═══════════════════════════════════════════════════════════════╝${NC}"
else
    echo -e "${RED}${BOLD}  ╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}${BOLD}  ║                                                               ║${NC}"
    echo -e "${RED}${BOLD}  ║   ✗ SOME TESTS FAILED                                         ║${NC}"
    echo -e "${RED}${BOLD}  ║                                                               ║${NC}"
    echo -e "${RED}${BOLD}  ╚═══════════════════════════════════════════════════════════════╝${NC}"
fi

echo ""
echo -e "  ${GREEN}Passed: $PASS_COUNT${NC}"
echo -e "  ${RED}Failed: $FAIL_COUNT${NC}"
echo -e "  ${WHITE}Total:  $TOTAL${NC}"
echo ""

echo -e "${CYAN}Security Features Verified:${NC}"
echo -e "  ${DIM}• Idempotency protection (replay attack prevention)${NC}"
echo -e "  ${DIM}• Server-side odds (client manipulation prevention)${NC}"
echo -e "  ${DIM}• Input validation (negative amounts, invalid drivers, XSS)${NC}"
echo -e "  ${DIM}• Balance protection (overdraft prevention)${NC}"
echo -e "  ${DIM}• Double-settlement prevention${NC}"
echo -e "  ${DIM}• Precision attack prevention (decimal places)${NC}"
echo -e "  ${DIM}• Injection protection (special characters)${NC}"
echo ""

echo -e "${DIM}Documentation: $BASE_URL/swagger-ui.html${NC}"
if [ "$NO_CACHE" = true ]; then
    echo -e "${DIM}Cache: disabled (fresh OpenF1 requests)${NC}"
else
    echo -e "${DIM}Cache: enabled (180s TTL)${NC}"
    echo -e "${DIM}Run with --no-cache to bypass: ./smoke-test.sh --no-cache${NC}"
fi
echo ""

exit $FAIL_COUNT
