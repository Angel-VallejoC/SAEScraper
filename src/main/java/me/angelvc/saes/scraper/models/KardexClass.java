package me.angelvc.saes.scraper.models;

public class KardexClass {

    private String clave;
    private String materia;
    private String fecha;
    private String periodo;
    private String formaEvaluacion;
    private String calificacion;

    public KardexClass(String clave, String materia, String fecha, String periodo, String formaEvaluacion, String calificacion) {
        this.clave = clave;
        this.materia = materia;
        this.fecha = fecha;
        this.periodo = periodo;
        this.formaEvaluacion = formaEvaluacion;
        this.calificacion = calificacion;
    }

    public String getClave() {
        return clave;
    }

    public String getMateria() {
        return materia;
    }

    public String getFecha() {
        return fecha;
    }

    public String getPeriodo() {
        return periodo;
    }

    public String getFormaEvaluacion() {
        return formaEvaluacion;
    }

    public String getCalificacion() {
        return calificacion;
    }

    @Override
    public String toString() {
        return "KardexClass{" +
                "clave='" + clave + '\'' +
                ", materia='" + materia + '\'' +
                ", fecha='" + fecha + '\'' +
                ", periodo='" + periodo + '\'' +
                ", formaEvaluacion='" + formaEvaluacion + '\'' +
                ", calificacion='" + calificacion + '\'' +
                '}';
    }
}
