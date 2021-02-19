package me.angelvc.saes.scraper.models;

import java.util.*;

public class Kardex {

    private final HashMap<Integer, ArrayList<KardexClass>> kardex;

    public Kardex() {
        kardex = new LinkedHashMap<>();
    }

    public void addClass(int level, KardexClass kardexClass) {
        if (!kardex.containsKey(level))
            kardex.put(level, new ArrayList<>());

        kardex.get(level).add(kardexClass);
    }

    public int size() {
        return kardex.size();
    }

    public ArrayList<KardexClass> getLevelClasses(int level) {

        if (!kardex.containsKey(level))
            throw new IllegalArgumentException("Level " + level + " does not exist");

        return kardex.get(level);
    }

    public Set<Integer> getLevels(){
        return kardex.keySet();
    }

    @Override
    public String toString() {
        return "Kardex{" +
                "kardex=" + kardex +
                '}';
    }
}
