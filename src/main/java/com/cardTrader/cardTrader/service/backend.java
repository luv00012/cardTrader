package com.cardTrader.cardTrader.service;

import com.cardTrader.cardTrader.repo.playerCardStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.cardTrader.cardTrader.controller.controllerClass;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.cardTrader.cardTrader.domain.Card;

import java.util.UUID;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;


import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
/**
 * TO IMPLEMENT: Use discord to login? --> maybe even look at using it as a sso?
 */
public class backend {
    private final playerCardStorage pCStorage;

    private final int currentSeason = 7;

    private ArrayList<Character> Alphabet = new ArrayList<Character>() {{
        for (char c = 'a'; c <= 'z'; c++) {
            add(c);
        }
    }};

    private Dictionary<String, String> teamsID = new Hashtable<>();
    
    public Card getCardByID(Long playerID) {
        Card cardOptional = pCStorage.getCardByplayerID(playerID).orElseThrow(() -> new RuntimeException("Card not found"));
        return cardOptional;
    }

    public Card updateCard(Card card) {
        Card existingCard = pCStorage.getCardByplayerID(card.getPlayerID()).orElseThrow(() -> new RuntimeException("Card not found"));

        existingCard.setPlayerClub(card.getPlayerClub());
        existingCard.setPlayerID(card.getPlayerID());
        existingCard.setPlayerImage(card.getPlayerImage());
        existingCard.setPlayerName(card.getPlayerName());
        existingCard.setPlayerNation(card.getPlayerNation());
        existingCard.setPlayerPosition(card.getPlayerPosition());
        existingCard.setPlayerRating(card.getPlayerRating());
        existingCard.setVersion(card.getVersion());

        return pCStorage.save(existingCard);
    }

    public Card addNewCard(Card card) {
        return pCStorage.save(card);
    }
    
    public void deleteCardByID(Long playerID) {
        pCStorage.delete(getCardByID(playerID));
    }

    public HashSet<Card> ScrapePSODatabase() {
    HashSet<Card> CardStack = new HashSet<>();
    HashSet<String> seenIDs = new HashSet<>();
    getTeamsID(); //creates the Team Dictionary
    for (char letter : Alphabet) {

        String PSOUrl = "https://psafdb.com/api/searchPlayerByName?s=" + letter;

        System.out.println("Scraping letter: " + letter);
        try {
            Document doc = connectMethod(PSOUrl);

            HashSet<String> IDs = new HashSet<>(getIDsFromScrape(doc));
            for (String id : IDs) {
                if (seenIDs.contains(id)) {
                    continue;
                }
                    try {
                            Card card = scrapePSOCardDetails(id);
                            if(card!=null) {

                                CardStack.add(card);
                                seenIDs.add(id);
                        }

                    } catch (Exception e) {
                        System.err.println("Failed to fetch details for ID: " + id + " - " + e.getMessage());
                    }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch data for letter: " + "a" + " - " + e.getMessage());
            }
        }
        return CardStack;
    }

    public Card scrapePSOCardDetails(String ID) {

        String playerURL = "https://psafdb.com/api/multiplayer?playerIds=";
        String GetimageUrl = "https://psafdb.com/api/playerImage/";
        
        try {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Document cardDetailsDoc = connectMethod(playerURL + ID);


            String json = cardDetailsDoc.body().text();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode player = root.path(ID);
            JsonNode discUser = player.path("user");
            if (!player.path("nat1").isMissingNode() && !discUser.path("global_name").isMissingNode() && !player.path("rating").isMissingNode() && player.path("rating").asInt()!=0
            && !player.path("contracts").isMissingNode() && !player.path("mostCommonPosition").isMissingNode()) { 
                //if the player does not have an these fields, the card is outdated so we return null
                //and don't add it to the database
                
            Long playerID = Long.parseLong(ID);
            String nation = player.path("nat1").asText();
            int rating = player.path("rating").asInt();
            String playerPosition = player.path("mostCommonPosition").asText();

            String name = discUser.path("global_name").asText();

            JsonNode contracts = player.path("contracts");
            String clubId = "0";
            String club = "Free Agent";

            JsonNode lastContract = contracts.get(contracts.size() - 1); // Get the last contract

            if (contracts.isArray() && contracts.size() > 0 && lastContract.path("until").asInt() >= currentSeason) {

                clubId = lastContract.path("team").asText(); // Extract the 'team' field
                System.out.println("Club ID: " + clubId);

                if (teamsID.get(clubId) != null) {
                    club = teamsID.get(clubId);

                } else {
                    club = teamsID.get("0");  // If the team ID is not found, set the club to "Free Agent"
                } 
            }

            // Sleep for 1 second to avoid rate limiting         

            String imageUrl = GetimageUrl+ID;


            System.out.println("Player ID: " + playerID);
            System.out.println("Rating: " + rating);
            System.out.println("Name: " + name);
            System.out.println("Nation: " + nation);
            System.out.println("Club: " + club);
            System.out.println("Image URL: " + imageUrl);

            // Create and populate the Card object
            Card card = MakeNewCard(playerID, name, nation, club, rating, imageUrl, playerPosition);
            return card;
        }
        return null;
    }
        catch (IOException e) {
        throw new RuntimeException("Failed to fetch data from PSO API", e);
        }

        // } catch (Exception e) {
        //     throw new RuntimeException("Failed to scrape player details", e);
        // }
    }
    
    private Card MakeNewCard(Long playerID, String name, String nation, String club, int rating, String imageUrl, String playerPosition) {
        Card card = new Card();
        card.setPlayerID(playerID);
        card.setPlayerName(name);
        card.setPlayerNation(nation);
        card.setPlayerClub(club);
        card.setPlayerPosition(playerPosition);
        card.setPlayerRating(rating);
        card.setPlayerImage(imageUrl);
        return card;
    }

    public void getTeamsID() {
        String teamURL = "https://psafdb.com/api/teams";
        try {
            Document teamsDoc = connectMethod(teamURL);
    
            String jsonString = teamsDoc.body().text();
            ObjectMapper mapper = new ObjectMapper();
    
            // Parse the root array directly
            JsonNode rootArray = mapper.readTree(jsonString);
    
            for (JsonNode team : rootArray) {
                String teamName = team.path("name").asText();
                String teamID = team.path("id").asText();
    
                // Add the team name and ID to the map
                teamsID.put(teamID, teamName);
            }  
            teamsID.put("0", "Free Agent");

        
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from PSO Database", e);
        }
    }

    private HashSet<String> getIDsFromScrape(Document doc) {
        HashSet<String> ids = new HashSet<>();
        try {
            String jsonString = doc.body().text();
            // Create ObjectMapper
            ObjectMapper mapper = new ObjectMapper();

            // Parse JSON string into JsonNode
            JsonNode rootNode = mapper.readTree(jsonString);

            // Navigate to the "players" array
            JsonNode playersArray = rootNode.path("players");

            // Extract "id" tags
            if (!playersArray.isMissingNode() && playersArray.isArray()) {
                // Extract "id" tags
                for (JsonNode player : playersArray) {
                    String id = player.path("id").asText();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            } else {
                System.out.println("No 'players' array found or it's empty.");
            }
    
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error parsing JSON or extracting IDs.");
        }
        return ids;
    }

    private Document connectMethod(String url){
        try {
            Document doc = Jsoup.connect(url)
                .header("Accept", "application/json")
                .userAgent("Mozilla/5.0 (Windows NT 6.1; rv:40.0) Gecko/20100101 Firefox/40.0")
                .ignoreContentType(true)
                .get();
            return doc;
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect", e);
        }
    }

    private String makeOrCheckFileExistance(String FilePath){ 
        File file = new File(FilePath); 
            try {
                file.createNewFile();
                return FilePath;
            } catch (Exception e) { //if the file already exists
                return FilePath;
            }
    } 

    public void addNewCardToCSV(Card Card) {
        String FilePath = "C:\\Projects\\cardTrader\\cardTrader\\src\\main\\java\\com\\cardTrader\\cardTrader\\CSVFiles\\cardData.csv";
        try { 
            File file = new File(makeOrCheckFileExistance(FilePath)); 
            
            // create FileWriter object with file as parameter 
            FileWriter outputfile = new FileWriter(file, true); 
  
            // create CSVWriter object filewriter object as parameter 
            CSVWriter writer = new CSVWriter(outputfile); 
            
            if (file.length()==0){
                // adding header to csv 
                String[] header = { "Player ID", "Player Name", "Nation" , "Club", "Rating", "Image URL", "Position"}; 
                writer.writeNext(header);
            }

            // add data to csv 
            String[] data = {Card.getPlayerID().toString(), Card.getPlayerName(), Card.getPlayerNation(), Card.getPlayerClub(), String.valueOf(Card.getPlayerRating()), Card.getPlayerImage(), Card.getPlayerPosition()}; 
            writer.writeNext(data); 
  
            // closing writer connection 
            writer.close(); 
        }
        catch (IOException e) { 
            e.printStackTrace(); 
        } 
    }
}
