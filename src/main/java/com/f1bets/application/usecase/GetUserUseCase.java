package com.f1bets.application.usecase;

import com.f1bets.domain.exception.UserNotFoundException;
import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.User;
import com.f1bets.domain.model.UserId;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetUserUseCase {

    private final UserRepository userRepository;
    private final BetRepository betRepository;

    public GetUserUseCase(UserRepository userRepository, BetRepository betRepository) {
        this.userRepository = userRepository;
        this.betRepository = betRepository;
    }

    @Transactional(readOnly = true)
    public UserWithBets execute(UserId userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        List<Bet> bets = betRepository.findByUserId(userId);

        return new UserWithBets(user, bets);
    }

    public record UserWithBets(User user, List<Bet> bets) {}
}
