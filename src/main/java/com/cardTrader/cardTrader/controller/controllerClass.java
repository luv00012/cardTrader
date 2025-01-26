package com.cardTrader.cardTrader.controller;
import java.util.ArrayList;
import java.util.HashSet;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cardTrader.cardTrader.domain.Card;
import com.cardTrader.cardTrader.service.backend;
//import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/cards")
@CrossOrigin(origins = "http://localhost:8080")
public class controllerClass {
 
    private final backend Backend;

    public controllerClass(backend Backend) {
        this.Backend = Backend;
    }

    @PostMapping("/add")
    public ResponseEntity<Card> addCard(@RequestBody Card card) {
        Card newCard = Backend.addNewCard(card);
        return ResponseEntity.status(HttpStatus.CREATED).body(newCard);
    }

    @GetMapping("/{playerID}")
    public ResponseEntity<Card> getCard(@PathVariable Long playerID) {
        try {
            Card foundCard = Backend.getCardByID(playerID);
            return ResponseEntity.ok(foundCard);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/{playerID}")
    public ResponseEntity<String> deleteCard(@PathVariable Long playerID) {
        try {
            Backend.deleteCardByID(playerID);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("card deleted");
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("card not found");
        }
    }

    @GetMapping("/scrape/{Password}")
    public ResponseEntity<ArrayList<Card>> scrapeCardData(@PathVariable String Password) {
        if(Password.equals("LUCAMAX")) {
            try {

                ArrayList<Card> CardStack = new ArrayList<>(Backend.ScrapePSODatabase());
                for(int i = 0; i<CardStack.size(); i++) {
                    Backend.addNewCard(CardStack.get(i));
                }
                return ResponseEntity.ok(CardStack);
                
            } catch (RuntimeException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Return 400 if IMDb scraping fails
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }

    @PostMapping("/postScrape/{Password}")
    public ResponseEntity<String> postScrapedCardData(@PathVariable String Password) {
        if(Password.equals("LUCAMAX")) {
            try{

                // for(int i = 0; i<CardStack.size(); i++) {
                //     Backend.addNewCard(CardStack.get(i));
                // }

                return ResponseEntity.status(HttpStatus.ACCEPTED).body("card deleted");
                
            } catch (RuntimeException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Return 400 if IMDb scraping fails
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }

    @GetMapping("/test/{Password}")
    public ResponseEntity<String> a(@PathVariable String Password) {
        if(Password.equals("LUCAMAX")) {
            Backend.getTeamsID();
           return ResponseEntity.status(HttpStatus.ACCEPTED).body("Right Password. You didnt see anything ;)");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Wrong Password. You didnt see anything ;)");
    }

    @PutMapping("update/{playerId}")
        public ResponseEntity<Card> putMethodName(@PathVariable Long playerId, @RequestBody Card entity){ //Authentication Authentication) {
            try {
                //String username = ((UserDetails) Authentication.getPrincipal()).getUsername();
                //long userId = Backend.getUserIdbyUsername(username);
                Card existingCard = Backend.getCardByID(playerId);
                
                existingCard.setPlayerClub(entity.getPlayerClub());
                existingCard.setPlayerID(entity.getPlayerID());
                existingCard.setPlayerImage(entity.getPlayerImage());
                existingCard.setPlayerName(entity.getPlayerName());
                existingCard.setPlayerNation(entity.getPlayerNation());
                existingCard.setPlayerPosition(entity.getPlayerPosition());
                existingCard.setPlayerRating(entity.getPlayerRating());
                existingCard.setVersion(entity.getVersion());

                Card UpdatedCard = Backend.updateCard(existingCard);
                return ResponseEntity.ok(UpdatedCard);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        }

    @GetMapping("/scrapeToCSV/{Password}")
    public ResponseEntity<String> scrapeCardDataToCSV(@PathVariable String Password) {
        if(Password.equals("LUCAMAX")) {
            try {

                ArrayList<Card> CardStack = new ArrayList<>(Backend.ScrapePSODatabase());
                for (Card card : CardStack) {
                    Backend.addNewCardToCSV(card);
                }
                return ResponseEntity.ok("Check your CSV File");
                
            } catch (RuntimeException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Return 400 if IMDb scraping fails
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }

    @PostMapping("/StartBackend")
        public ResponseEntity<String> startBackend() {
            try {
                Backend.initializeBackend();
                return ResponseEntity.status(HttpStatus.OK).body("Initialized the Backend");
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Couldn't initialize the Backend");
        }
    }

    @PostMapping("/scrapeMatchDataToCSV/{Password}")
        public ResponseEntity<String> postMethodName(@PathVariable String Password) {
            if(Password.equals("LUCAMAX")) {
                try {
                    ArrayList<Long> playerIDs = new ArrayList<>(Backend.readPlayerIdsFromCSV("C:\\Projects\\cardTrader\\cardTrader\\src\\main\\java\\com\\cardTrader\\cardTrader\\CSVFiles\\cardData.csv"));
                    for (long player : playerIDs) {
                        HashSet<Long> MatchIDs = new HashSet<>(Backend.getPlayerMatchStatsAndIds(player));
                        Backend.addMatchInfoToCSV(MatchIDs);
                    }
                } catch (Exception e) {
                    System.err.println("Something went wrong in the backend");
                }
            }
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wrong Password");
        }
            
}
    
    
        

   
