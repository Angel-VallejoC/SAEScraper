package me.angelvc.saes.scraper.util;

public class Pair<T, P> {

    private T key;
    private P value;

    public Pair(T key, P value){
        this.key = key;
        this.value = value;
    }

    public T getKey() {
        return key;
    }

    public void setKey(T key) {
        this.key = key;
    }

    public P getValue() {
        return value;
    }

    public void setValue(P value) {
        this.value = value;
    }
}
