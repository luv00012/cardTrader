package com.cardTrader.cardTrader.service;

public class League {
    private String leagueName;
    private Long leagueID;
    private boolean is6v6; // true if 6v6, false if 8v8

    public League(String leagueName, Long leagueID, boolean is6v6) {
        this.leagueName = leagueName;
        this.leagueID = leagueID;
        this.is6v6 = is6v6;
    }

    public boolean is6v6() {
        return is6v6;
    }

    public Long getLeagueID() {
        return leagueID;
    }

    public String getLeagueName() {
        return leagueName;
    }
}
