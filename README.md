# Acerca

SAEScraper es un herramienta de web scraping que ayuda a recolectar
información del Sistema de Administración Escolar del 
Instituto Politécnico Nacional.

# Uso

### Obtener instancia del scraper
```java
SAEScraper scraper = SAEScraper.getInstance(SAESchoolsUrls.School.UPIICSA); // se reemplaza con la escuela deseada
```

### Inicio de sesión
Primero se carga la página principal/de inicio de sesión.
<br>
El SAES cuenta con un captcha requerido para iniciar sesión, 
al llamar al método loadLoginPage() la imagen es obtenida como 
un arreglo de bytes.
```java
byte[] captchaImage = scraper.loadLoginPage();
```

Posteriormente se llama al método login para iniciar sesión, pasando como parámetros 
el usuario, contraseña y el texto que tiene la imagen captcha.

```java
Pair<Boolean, String> result = scraper.login(user, password, captcha);
```

### Obtener información del alumno

<br>

#### Información general
```java
StudentInfo info = scraper.getStudentInfo();
```
<br>

#### Kardex
```java
Kardex kardex = scraper.getKardex();
```
<br>

#### Horario de la inscripción actual
```java
List<ScheduleClass> schedule = scraper.getStudentSchedule();
```
<br>

#### Calificaciones del semestre
```java
ArrayList<GradeEntry> grades = scraper.getGrades();
```
