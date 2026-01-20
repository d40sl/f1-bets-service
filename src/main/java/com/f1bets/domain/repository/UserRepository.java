package com.f1bets.domain.repository;

import com.f1bets.domain.model.User;
import com.f1bets.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByIdForUpdate(UserId id);

    boolean insertIfAbsent(User user);

    User save(User user);
}
