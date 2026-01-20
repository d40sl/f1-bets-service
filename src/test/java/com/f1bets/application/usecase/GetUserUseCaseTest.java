package com.f1bets.application.usecase;

import com.f1bets.domain.exception.UserNotFoundException;
import com.f1bets.domain.model.*;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BetRepository betRepository;

    private GetUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetUserUseCase(userRepository, betRepository);
    }

    @Nested
    @DisplayName("User retrieval")
    class UserRetrieval {

        @Test
        @DisplayName("should return user with bets when user exists")
        void shouldReturnUserWithBets() {
            UserId userId = UserId.of("test-user");
            User user = User.createNew(userId);
            Bet bet = Bet.place(
                userId,
                SessionKey.of(9158),
                DriverNumber.of(1),
                Money.ofCents(2500),
                Odds.of(3)
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(betRepository.findByUserId(userId)).thenReturn(List.of(bet));

            GetUserUseCase.UserWithBets result = useCase.execute(userId);

            assertEquals(userId.getValue(), result.user().getId().getValue());
            assertEquals(1, result.bets().size());
            assertEquals(bet.getId(), result.bets().get(0).getId());
        }

        @Test
        @DisplayName("should return user with empty bets when user has no bets")
        void shouldReturnUserWithEmptyBets() {
            UserId userId = UserId.of("test-user");
            User user = User.createNew(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(betRepository.findByUserId(userId)).thenReturn(List.of());

            GetUserUseCase.UserWithBets result = useCase.execute(userId);

            assertEquals(userId.getValue(), result.user().getId().getValue());
            assertTrue(result.bets().isEmpty());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            UserId userId = UserId.of("non-existent");
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> useCase.execute(userId));
        }
    }
}
