package me.angelvc.saes.scraper.models;

import java.util.Arrays;

public class ScheduleClass {

    private String grupo, materia, profesor, edificio, salon;
    private String horario[] = new String[5];

    public ScheduleClass(String grupo, String materia, String profesor, String edificio, String salon, String[] horario) {
        this.grupo = grupo;
        this.materia = materia;
        this.profesor = profesor;
        this.edificio = edificio;
        this.salon = salon;
        this.horario = horario;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getMateria() {
        return materia;
    }

    public String getProfesor() {
        return profesor;
    }

    public String getEdificio() {
        return edificio;
    }

    public String getSalon() {
        return salon;
    }

    public String[] getHorario() {
        return horario;
    }

    @Override
    public String toString() {
        return "ScheduleClass{" +
                "grupo='" + grupo + '\'' +
                ", materia='" + materia + '\'' +
                ", profesor='" + profesor + '\'' +
                ", edificio='" + edificio + '\'' +
                ", salon='" + salon + '\'' +
                ", horario=" + Arrays.toString(horario) +
                '}';
    }
}
