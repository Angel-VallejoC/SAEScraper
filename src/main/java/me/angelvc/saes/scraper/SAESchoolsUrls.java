package me.angelvc.saes.scraper;

import java.util.HashMap;

public class SAESchoolsUrls {
    public enum School {
        ESIME_AZC,
        ESIME_CU,
        ESIME_TICOMAN,
        ESIME_ZAC,
        ESIA_TEC,
        ESIA_TICOMAN,
        ESIA_ZAC,
        CICS_MILPA,
        CICS_SANTO,
        ESCA_SANTO,
        ESCA_TEPEPAN,
        ENCB,
        ENMH,
        ESEO,
        ESM,
        ESE,
        EST,
        UPIBI,
        UPIICSA,
        UPIITA,
        ESCOM,
        ESFM,
        ESIQIE,
        ESIT,
        UPIIG,
        UPIIH,
        UPIIZ,
        ENBA,
        UPIIC,
        UPIIP,
        UPIEM
    }

    private static HashMap<School, String> schoolsUrls;

    static {
        schoolsUrls = new HashMap<>();
        schoolsUrls.put(School.ESIME_AZC, "https://www.saes.esimeazc.ipn.mx/");
        schoolsUrls.put(School.ESIME_CU, "https://www.saes.esimecu.ipn.mx/");
        schoolsUrls.put(School.ESIME_TICOMAN, "https://www.saes.esimetic.ipn.mx/");
        schoolsUrls.put(School.ESIME_ZAC, "https://www.saes.esimecu.ipn.mx/");
        schoolsUrls.put(School.ESIA_TEC, "https://www.saes.esiatec.ipn.mx/");
        schoolsUrls.put(School.UPIICSA, "https://www.saes.upiicsa.ipn.mx/");
    }

    public static String getSchoolUrl(School school){
        return schoolsUrls.get(school);
    }

}
