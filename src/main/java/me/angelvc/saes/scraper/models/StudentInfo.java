package me.angelvc.saes.scraper.models;

public class StudentInfo {

    private String unidad;
    private String boleta;
    private String nombre;
    private String carrera;
    private String plan;
    private String promedio;

    public StudentInfo(String unidad, String boleta, String nombre, String carrera, String plan, String promedio) {
        this.unidad = unidad;
        this.boleta = boleta;
        this.nombre = nombre;
        this.carrera = carrera;
        this.plan = plan;
        this.promedio = promedio;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getBoleta() {
        return boleta;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCarrera() {
        return carrera;
    }

    public String getPlan() {
        return plan;
    }

    public String getPromedio() {
        return promedio;
    }

    @Override
    public String toString() {
        return "StudentInfo{" +
                "unidad='" + unidad + '\'' +
                ", boleta='" + boleta + '\'' +
                ", nombre='" + nombre + '\'' +
                ", carrera='" + carrera + '\'' +
                ", plan='" + plan + '\'' +
                ", promedio='" + promedio + '\'' +
                '}';
    }
}
