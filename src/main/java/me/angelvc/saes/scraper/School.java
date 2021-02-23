package me.angelvc.saes.scraper;
public enum School {
    CET_1("CET 1", "https://www.saes.cet1.ipn.mx/"),
    CECYT_1("CECyT 1", "https://www.saes.cecyt1.ipn.mx/"),
    CECYT_2("CECyT 2", "https://www.saes.cecyt2.ipn.mx/"),
    CECYT_3("CECyT 3", "https://www.saes.cecyt3.ipn.mx/"),
    CECYT_4("CECyT 4", "https://www.saes.cecyt4.ipn.mx/"),
    CECYT_5("CECyT 5", "https://www.saes.cecyt5.ipn.mx/"),
    CECYT_6("CECyT 6", "https://www.saes.cecyt6.ipn.mx/"),
    CECYT_7("CECyT 7", "https://www.saes.cecyt7.ipn.mx/"),
    CECYT_8("CECyT 8", "https://www.saes.cecyt8.ipn.mx/"),
    CECYT_9("CECyT 9", "https://www.saes.cecyt9.ipn.mx/"),
    CECYT_10("CECyT 10", "https://www.saes.cecyt10.ipn.mx/"),
    CECYT_11("CECyT 11", "https://www.saes.cecyt11.ipn.mx/"),
    CECYT_12("CECyT 12", "https://www.saes.cecyt12.ipn.mx/"),
    CECYT_13("CECyT 13", "https://www.saes.cecyt13.ipn.mx/"),
    CECYT_14("CECyT 14", "https://www.saes.cecyt14.ipn.mx/"),
    CECYT_15("CECyT 15", "https://www.saes.cecyt15.ipn.mx/"),
    CECYT_16("CECyT 16", "https://www.saes.cecyt16.ipn.mx/"),
    CECYT_17("CECyT 17", "https://www.saes.cecyt17.ipn.mx/"),
    CECYT_18("CECyT 18", "https://www.saes.cecyt18.ipn.mx/"),
    CECYT_19("CECyT 19", "https://www.saes.cecyt19.ipn.mx/"),

    CICS_MILPA_ALTA("CICS Milpa Alta", "https://www.saes.cicsma.ipn.mx/"),
    CICS_STO_TOMAS("CICS Santo Tomás", "https://www.saes.cicsst.ipn.mx/"),
    ENBA("ENBA", "https://www.saes.enba.ipn.mx/"),
    ENCB("ENCB", "https://www.saes.encb.ipn.mx/"),
    ENMH("ENMH", "https://www.saes.enmh.ipn.mx/"),
    ESCA_STO_TOMAS("ESCA Santo Tomás", "https://www.saes.escasto.ipn.mx/"),
    ESCA_TEPEPAN("ESCA Tepepan", "https://www.saes.escatep.ipn.mx/"),
    ESCOM("ESCOM", "https://www.saes.escom.ipn.mx/"),
    ESE("ESE", "https://www.saes.ese.ipn.mx/"),
    ESEO("ESEO", "https://www.saes.eseo.ipn.mx/"),
    ESFM("ESFM", "https://www.saes.esfm.ipn.mx/"),
    ESIA_TEC("ESIA Tecamachalco", "https://www.saes.esiatec.ipn.mx/"),
    ESIA_TICOMAN("ESIA Ticomán", "https://www.saes.esiatic.ipn.mx/"),
    ESIA_ZAC("ESIA Zacatenco", "https://www.saes.esiaz.ipn.mx/"),
    ESIME_AZC("ESIME Azcapotzalco", "https://www.saes.esimeazc.ipn.mx/"),
    ESIME_CU("ESIME Culhuacan", "https://www.saes.esimecu.ipn.mx/"),
    ESIME_TICOMAN("ESIME Ticomán", "https://www.saes.esimetic.ipn.mx/"),
    ESIME_ZAC("ESIME Zacatenco", "https://www.saes.esimez.ipn.mx/"),
    ESIQIE("ESIQIE", "https://www.saes.esiqie.ipn.mx/"),
    ESIT("ESIT", "https://www.saes.esit.ipn.mx/"),
    ESM("ESM", "https://www.saes.esm.ipn.mx/"),
    EST("EST", "https://www.saes.est.ipn.mx/"),
    UPIBI("UPIBI", "https://www.saes.upibi.ipn.mx/"),
    UPIEM("UPIEM", "https://www.saes.upiem.ipn.mx/"),
    UPIIC("UPIIC", "https://www.saes.upiic.ipn.mx/"),
    UPIICSA("UPIICSA", "https://www.saes.upiicsa.ipn.mx/"),
    UPIIG("UPIIG", "https://www.saes.upiig.ipn.mx/"),
    UPIIH("UPIIH", "https://www.saes.upiih.ipn.mx/"),
    UPIIP("UPIIP", "https://www.saes.upiip.ipn.mx/"),
    UPIITA("UPIITA", "https://www.saes.upiita.ipn.mx/"),
    UPIIZ("UPIIZ", "https://www.saes.upiiz.ipn.mx/");

    public final String name, url;

    School(String name, String url) {
        this.name = name;
        this.url = url;

    }

    public static School getSchoolByName(String name) {
        for (School e : School.values()) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}

