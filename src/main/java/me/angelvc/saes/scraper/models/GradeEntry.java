package me.angelvc.saes.scraper.models;

public class GradeEntry {

    private final String code, className, first, second, third, extra, finalGrade;

    public GradeEntry(String code, String className, String first, String second, String third, String extra, String finalGrade) {
        this.code = code;
        this.className = className;
        this.first = first;
        this.second = second;
        this.third = third;
        this.extra = extra;
        this.finalGrade = finalGrade;
    }

    public String getCode() {
        return code == null ? "-" : code;
    }

    public String getClassName() {
        return className == null ? "-" : className;
    }

    public String getFirst() {
        return first == null ? "-" : first;
    }

    public String getSecond() {
        return second == null ? "-" : second;
    }

    public String getThird() {
        return third == null ? "-" : third;
    }

    public String getExtra() {
        return extra == null ? "-" : extra;
    }

    public String getFinalGrade() {
        return finalGrade == null ? "-" : finalGrade;
    }

    @Override
    public String toString() {
        return "GradeEntry{" +
                "code='" + code + '\'' +
                ", className='" + className + '\'' +
                ", first='" + first + '\'' +
                ", second='" + second + '\'' +
                ", third='" + third + '\'' +
                ", extra='" + extra + '\'' +
                ", finalGrade='" + finalGrade + '\'' +
                '}';
    }
}
