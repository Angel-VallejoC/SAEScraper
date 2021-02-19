package me.angelvc.saes.scraper.models;

public class StudentInfo {

    private final String campus;
    private final String id;
    private final String name;
    private final String degreeName;
    private final String plan;
    private final String average;

    public StudentInfo(String campus, String id, String name, String degreeName, String plan, String average) {
        this.campus = campus;
        this.id = id;
        this.name = name;
        this.degreeName = degreeName;
        this.plan = plan;
        this.average = average;
    }

    public String getCampus() {
        return campus;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDegreeName() {
        return degreeName;
    }

    public String getPlan() {
        return plan;
    }

    public String getAverage() {
        return average;
    }

    @Override
    public String toString() {
        return "StudentInfo{" +
                "campus='" + campus + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", degreeName='" + degreeName + '\'' +
                ", plan='" + plan + '\'' +
                ", average='" + average + '\'' +
                '}';
    }
}
