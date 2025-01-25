package com.cardTrader.cardTrader.repo;

import java.util.Optional;
import java.util.UUID;

import com.cardTrader.cardTrader.domain.Card;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface playerCardStorage extends JpaRepository<Card, UUID> {
    Optional<Card> getCardByplayerID(Long playerID);
}

