package com.campuslostfound.domain;

/** Why a listing was flagged for moderator attention. */
public enum FlagReason {
    SPAM,
    SCAM,
    OFFENSIVE,
    WRONG_INFO,
    PROHIBITED_ITEM,
    OTHER
}
