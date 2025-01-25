    package com.cardTrader.cardTrader.domain;

    import java.util.UUID;

import org.hibernate.annotations.GenericGenerator;

    import com.fasterxml.jackson.annotation.JsonInclude;

    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;
    import jakarta.persistence.Version;

    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

    @Entity
    @Table(name = "Cards")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public class Card {

        @Id
        @GeneratedValue(generator = "uuid")
        @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
        @Column(name = "generatedID", unique = true, updatable = false, nullable = false)
        private UUID generatedID;
        
        @Column(name = "playerID", nullable = false)
        private Long playerID;
        
        @Column(name = "playerName", nullable = false)
        private String playerName;

        @Column(name = "playerNation")
        private String playerNation;

        @Column(name = "playerClub")
        private String playerClub;

        @Column(name = "playerPosition")
        private String playerPosition;

        @Column(name = "playerImage")
        private String playerImage;

        @Column(name = "playerRating")
        private int playerRating;

        @Version
        @Column(name = "version")
        private long version;
    }