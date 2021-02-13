package me.angelvc.saes.scraper.models;

public class GradeEntry {

    private String grupo, materia, primero, segundo, tercero, extra, calFinal;

    public GradeEntry(String grupo, String materia, String primero, String segundo, String tercero, String extra, String calFinal) {
        this.grupo = grupo;
        this.materia = materia;
        this.primero = primero;
        this.segundo = segundo;
        this.tercero = tercero;
        this.extra = extra;
        this.calFinal = calFinal;
    }

    public String getGrupo() {
        return grupo == null ? "-" : grupo;
    }

    public String getMateria() {
        return materia == null ? "-" : materia;
    }

    public String getPrimero() {
        return primero == null ? "-" : primero;
    }

    public String getSegundo() {
        return segundo == null ? "-" : segundo;
    }

    public String getTercero() {
        return tercero == null ? "-" : tercero;
    }

    public String getExtra() {
        return extra == null ? "-" : extra;
    }

    public String getCalFinal() {
        return calFinal == null ? "-" : calFinal;
    }

    @Override
    public String toString() {
        return "GradeEntry{" +
                "grupo='" + grupo + '\'' +
                ", materia='" + materia + '\'' +
                ", primero='" + primero + '\'' +
                ", segundo='" + segundo + '\'' +
                ", tercero='" + tercero + '\'' +
                ", extra='" + extra + '\'' +
                ", calFinal='" + calFinal + '\'' +
                '}';
    }
}
