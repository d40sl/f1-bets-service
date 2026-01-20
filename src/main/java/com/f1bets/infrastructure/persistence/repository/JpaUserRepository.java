package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.domain.model.User;
import com.f1bets.domain.model.UserId;
import com.f1bets.domain.repository.UserRepository;
import com.f1bets.infrastructure.persistence.mapper.EntityMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository springDataRepository;
    private final EntityMapper mapper;

    public JpaUserRepository(SpringDataUserRepository springDataRepository, EntityMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return springDataRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByIdForUpdate(UserId id) {
        return springDataRepository.findByIdForUpdate(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public boolean insertIfAbsent(User user) {
        int inserted = springDataRepository.insertIfAbsent(
            user.getId().getValue(),
            user.getBalanceCents(),
            user.getVersion(),
            user.getCreatedAt()
        );
        return inserted == 1;
    }

    @Override
    public User save(User user) {
        var entity = mapper.toJpa(user);
        var saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
