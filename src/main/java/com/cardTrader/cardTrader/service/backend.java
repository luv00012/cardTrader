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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;

import java.util.Dictionary;
import java.util.Scanner;
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

    private ArrayList<League> leagues = new ArrayList<>();

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

    public ArrayList<Long> readPlayerIdsFromCSV(String FilePath) {
        ArrayList<Long> playerIds = new ArrayList<>();
        try {
            File file = new File(FilePath);
            Scanner sc = new Scanner(file);
            // Skip the header line
            if (sc.hasNextLine()) {
                sc.nextLine();
            }
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] values = line.split(",");
                if (values.length > 0) {
                    playerIds.add(Long.parseLong(values[0]));
                }
            }
            sc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playerIds;
    }

    public ArrayList<League> scrapeLeagues(){
        String url = "https://psafdb.com/api/leagues";
        try{
            Document doc = connectMethod(url);
    
            String jsonString = doc.body().text();
            ObjectMapper mapper = new ObjectMapper();
    
            // Parse the root array directly
            JsonNode rootArray = mapper.readTree(jsonString);
    
            for (JsonNode league : rootArray) {
                String leagueName = league.path("name").asText();
                Long leagueID = league.path("value").asLong();
                int noOfPlayers = league.path("players").asInt();

                if (noOfPlayers == 6) {
                    leagues.add(new League(leagueName, leagueID, true));
                } else if (noOfPlayers == 8) {
                    leagues.add(new League(leagueName, leagueID, false));
                }
            }         
        }
        catch (Exception e){
            throw new RuntimeException("Failed to fetch data from PSO Database", e);
        }
        return leagues;
    }

    public HashSet<Long> getPlayerMatchStatsAndIds(Long playerID) {
        HashSet<Long> matchIds = new HashSet<>();
        String url = "https://psafdb.com/api/multiplayer?playerIds=" + playerID;
    
        Long matchId = 0L;
        String homeAway = "";
        String team = "";
        String pos = "";
        String nameInGame = "";
        int score = 0;
        int passes = 0;
        int assists = 0;
        int shots = 0;
        int goals = 0;
        int tackles = 0;
        int interceptions = 0;
        int gkCatches = 0;
        int gkSaves = 0;
    
        try {
            Document matchDoc = connectMethod(url);
            String jsonString = matchDoc.body().text();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
    
            JsonNode matchStats = root.path("stats");
    
            for (JsonNode match : matchStats) {
                if (!match.path("matchId").isMissingNode() && !match.path("matchId").isNull()
                        && !match.path("homeAway").isMissingNode() && !match.path("homeAway").isNull()
                        && !match.path("team").isMissingNode() && !match.path("team").isNull()
                        && !match.path("pos").isMissingNode() && !match.path("pos").isNull()
                        && !match.path("name").isMissingNode() && !match.path("name").isNull()
                        && !match.path("Score").isMissingNode() && !match.path("Score").isNull()
                        && !match.path("Passes").isMissingNode() && !match.path("Passes").isNull()
                        && !match.path("Assists").isMissingNode() && !match.path("Assists").isNull()
                        && !match.path("Shots").isMissingNode() && !match.path("Shots").isNull()
                        && !match.path("Goals").isMissingNode() && !match.path("Goals").isNull()
                        && !match.path("Tackles").isMissingNode() && !match.path("Tackles").isNull()
                        && !match.path("Interceptions").isMissingNode() && !match.path("Interceptions").isNull()
                        && !match.path("GK Catches").isMissingNode() && !match.path("GK Catches").isNull()
                        && !match.path("GK Saves").isMissingNode() && !match.path("GK Saves").isNull()
                        && !match.path("id").isMissingNode() && !match.path("id").isNull()) {
    
                    matchId = match.path("matchId").asLong();
                    matchIds.add(matchId);
    
                    homeAway = match.path("homeAway").asText();
    
                    String clubId = match.path("team").asText();
                    if (teamsID.get(clubId) != null) {
                        team = teamsID.get(clubId);
                    } else {
                        team = "N/A";
                    }
    
                    pos = match.path("pos").asText();
                    nameInGame = match.path("name").asText();
                    score = match.path("Score").asInt();
                    passes = match.path("Passes").asInt();
                    assists = match.path("Assists").asInt();
                    shots = match.path("Shots").asInt();
                    goals = match.path("Goals").asInt();
                    tackles = match.path("Tackles").asInt();
                    interceptions = match.path("Interceptions").asInt();
                    gkCatches = match.path("GK Catches").asInt();
                    gkSaves = match.path("GK Saves").asInt();
                }
            }
    
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch match stats", e);
        }
        String FilePath = "C:\\Projects\\cardTrader\\cardTrader\\src\\main\\java\\com\\cardTrader\\cardTrader\\CSVFiles\\playersMatchData.csv";
        try {
            File file = new File(makeOrCheckFileExistance(FilePath));
    
            // create FileWriter object with file as parameter
            FileWriter outputfile = new FileWriter(file, true);
    
            // create CSVWriter object filewriter object as parameter
            CSVWriter writer = new CSVWriter(outputfile);
    
            if (file.length() == 0) {
                // adding header to csv
                String[] header = { "Player ID", "Match ID", "homeAway", "Team", "Position", "IGN", "Score", "Passes",
                        "Assists", "Shots", "Goals", "Tackles", "Interceptions", "GK Catches", "GK Saves" };
                writer.writeNext(header);
            }
    
            // add data to csv
            String[] data = { playerID.toString(), matchId.toString(), homeAway, team, pos, nameInGame,
                    String.valueOf(score), String.valueOf(passes), String.valueOf(assists), String.valueOf(shots),
                    String.valueOf(goals), String.valueOf(tackles), String.valueOf(interceptions),
                    String.valueOf(gkCatches), String.valueOf(gkSaves) };
            writer.writeNext(data);
    
            // closing writer connection
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return matchIds;
    }
    
    public void addMatchInfoToCSV(HashSet<Long> matchIds) {
        String FilePath = "C:\\Projects\\cardTrader\\cardTrader\\src\\main\\java\\com\\cardTrader\\cardTrader\\CSVFiles\\matchInfo.csv";
        try {

            ArrayList<ArrayList<String>> AllMatchData = new ArrayList<>(getAllMatchStats(matchIds));
            File file = new File(makeOrCheckFileExistance(FilePath));
    
            // create FileWriter object with file as parameter
            FileWriter outputfile = new FileWriter(file, true);
    
            // create CSVWriter object filewriter object as parameter
            CSVWriter writer = new CSVWriter(outputfile);
    
            if (file.length() == 0) {
                // adding header to csv
                String[] header = { "Match ID", "Home Team", "Away Team", "Date", "League", "Match Day", "Home Score",
                        "Away Score", "Is International", "Season", "Group", "Is FF" };
                writer.writeNext(header);
            }
    
            // add data to csv
            for (ArrayList<String> match : AllMatchData) {
                String[] data = { match.get(0), match.get(1), match.get(2), match.get(3), match.get(4), match.get(5),
                        match.get(6), match.get(7), match.get(8), match.get(9), match.get(10), match.get(11) };
                writer.writeNext(data);
            }
    
            // closing writer connection
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        }

    private ArrayList<ArrayList<String>>getAllMatchStats(HashSet<Long> matchIds){
        //put all the matches together into arraylist
        ArrayList<ArrayList<String>> MatchData = new ArrayList<>();

        for (long match : matchIds) {
            MatchData.add(getMatchInfo(match));
        }
        
        return MatchData;

    }

    private ArrayList<String> getMatchInfo(Long MatchId) {
        //get the matchtats

        ArrayList<String> MatchData = new ArrayList<>();


        String MatchURL = "https://psafdb.com/api/matches/" + MatchId;
        try {
            Document cardDetailsDoc = connectMethod(MatchURL);

            String json = cardDetailsDoc.body().text();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);            
            
            String matchID = root.path("matchId").asText();
            String date = root.path("dateTimestamp").asText();
            String League = root.path("league").asText();

            for (int i = 0; i<leagues.size(); i++) {
                if(leagues.get(i).getLeagueID().equals(League)) {
                    League = leagues.get(i).getLeagueName();
                    break;
                }
            }
            
            getMatchStats6v6(cardDetailsDoc,MatchId);
            
            String matchDay = root.path("matchday").asText();
            String homeScore = root.path("homeScore").asText();
            String awayScore = root.path("awayScore").asText();
            String isInternational = root.path("isInternational").asText();
            String season = root.path("season").asText();
            String group = root.path("group").asText();
            String isFF = root.path("isFF").asText();

            String homeTeam = "";
            String awayTeam = "";


            String homeClubId = root.path("home").asText();
                    if (teamsID.get(homeClubId) != null) {
                        homeTeam = teamsID.get(homeClubId);
                    } else {
                        homeTeam = "N/A";
                    }
                    
            String awayClubId = root.path("away").asText();
                if (teamsID.get(awayClubId) != null) {
                    awayTeam = teamsID.get(awayClubId);
                } else {
                    awayTeam = "N/A";
                }
            
            MatchData.add(matchID);
            MatchData.add(homeTeam);
            MatchData.add(awayTeam);
            MatchData.add(date);
            MatchData.add(League);
            MatchData.add(matchDay);
            MatchData.add(homeScore);
            MatchData.add(awayScore);
            MatchData.add(isInternational);
            MatchData.add(season);
            MatchData.add(group);
            MatchData.add(isFF);
            
            return MatchData;
        } catch (Exception e) {
            System.err.println("Couldn't add or get match data");
            return MatchData;
        }
    }

    public void getMatchStats6v6(Document doc, Long MatchId) {
        try {
            String jsonString = doc.body().text();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
            JsonNode match = root.path("awayStats");
            JsonNode match2 = root.path("homeStats");
            
            if (!match.path("Goals").isMissingNode() && !match.path("Goals").isNull()
                && !match.path("Possession").isMissingNode() && !match.path("Possession").isNull()
                && !match.path("Passes").isMissingNode() && !match.path("Passes").isNull()
                && !match.path("Assists").isMissingNode() && !match.path("Assists").isNull()
                && !match.path("Shots").isMissingNode() && !match.path("Shots").isNull()
                && !match.path("Tackles").isMissingNode() && !match.path("Tackles").isNull()
                && !match.path("Interceptions").isMissingNode() && !match.path("Interceptions").isNull()
                && !match.path("Fouls / Offsides").isMissingNode() && !match.path("Fouls / Offsides").isNull()
                && !match.path("Free Kicks").isMissingNode() && !match.path("Free Kicks").isNull()
                && !match.path("Penalties").isMissingNode() && !match.path("Penalties").isNull()
                && !match.path("Goal Kicks").isMissingNode() && !match.path("Goal Kicks").isNull()
                && !match.path("Corner Kicks").isMissingNode() && !match.path("Corner Kicks").isNull()
                && !match.path("Throw Ins").isMissingNode() && !match.path("Throw Ins").isNull()
                && !match.path("Yellow Cards").isMissingNode() && !match.path("Yellow Cards").isNull()
                && !match.path("Red Cards").isMissingNode() && !match.path("Red Cards").isNull()) {

                    }

            if (!match.path("Goals").isMissingNode() && !match.path("Goals").isNull()
                && !match.path("Possession").isMissingNode() && !match.path("Possession").isNull()
                && !match.path("Passes").isMissingNode() && !match.path("Passes").isNull()
                && !match.path("Assists").isMissingNode() && !match.path("Assists").isNull()
                && !match.path("Shots").isMissingNode() && !match.path("Shots").isNull()
                && !match.path("Tackles").isMissingNode() && !match.path("Tackles").isNull()
                && !match.path("Interceptions").isMissingNode() && !match.path("Interceptions").isNull()
                && !match.path("Fouls / Offsides").isMissingNode() && !match.path("Fouls / Offsides").isNull()
                && !match.path("Free Kicks").isMissingNode() && !match.path("Free Kicks").isNull()
                && !match.path("Penalties").isMissingNode() && !match.path("Penalties").isNull()
                && !match.path("Goal Kicks").isMissingNode() && !match.path("Goal Kicks").isNull()
                && !match.path("Corner Kicks").isMissingNode() && !match.path("Corner Kicks").isNull()
                && !match.path("Throw Ins").isMissingNode() && !match.path("Throw Ins").isNull()
                && !match.path("Yellow Cards").isMissingNode() && !match.path("Yellow Cards").isNull()
                && !match.path("Red Cards").isMissingNode() && !match.path("Red Cards").isNull()) {
    
                        }
            } catch (Exception e) {
                System.err.println("Something went wrong with the getMatchStats6v6");
        }   
     }

    //public void getPlayerMatchStatsByMatchID

    public void initializeBackend(){
        getTeamsID();
        scrapeLeagues();
    }

    // method for saving league details https://psafdb.com/api/leagues

    // method for saving match IDs by already created csv file

    // method for saving player match stats
    
    // method for saving the match stats by 6v6 or 8v8
}
