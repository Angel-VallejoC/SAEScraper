package me.angelvc.saes.scraper.models;

import java.util.Arrays;

public class ScheduleClass {

    private final String code, name, professor, building, classroom;
    private final String[] schedule;

    public ScheduleClass(String code, String name, String professor, String building, String classroom, String[] schedule) {
        this.code = code;
        this.name = name;
        this.professor = professor;
        this.building = building;
        this.classroom = classroom;
        this.schedule = schedule;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getProfessor() {
        return professor;
    }

    public String getBuilding() {
        return building;
    }

    public String getClassroom() {
        return classroom;
    }

    public String[] getSchedule() {
        return schedule;
    }

    @Override
    public String toString() {
        return "ScheduleClass{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", professor='" + professor + '\'' +
                ", building='" + building + '\'' +
                ", classroom='" + classroom + '\'' +
                ", schedule=" + Arrays.toString(schedule) +
                '}';
    }
}
