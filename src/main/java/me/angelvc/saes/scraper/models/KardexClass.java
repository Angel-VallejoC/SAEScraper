package me.angelvc.saes.scraper.models;

public class KardexClass {

    private final String code;
    private final String name;
    private final String date;
    private final String term;
    private final String evaluationType;
    private final String grade;

    public KardexClass(String code, String name, String date, String term, String evaluationType, String grade) {
        this.code = code;
        this.name = name;
        this.date = date;
        this.term = term;
        this.evaluationType = evaluationType;
        this.grade = grade;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getTerm() {
        return term;
    }

    public String getEvaluationType() {
        return evaluationType;
    }

    public String getGrade() {
        return grade;
    }

    @Override
    public String toString() {
        return "KardexClass{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", date='" + date + '\'' +
                ", term='" + term + '\'' +
                ", evaluationType='" + evaluationType + '\'' +
                ", grade='" + grade + '\'' +
                '}';
    }
}
