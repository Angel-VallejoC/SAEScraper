package me.angelvc.saes.scraper.exceptions;

public class SessionExpiredException extends IllegalStateException{
    public SessionExpiredException(String message){
        super(message);
    }
}
