SELECT generico_5.anunciante AS anunciante,   generico_5.anunciante AS datos_copia,   SUM(generico_5.inversionus) AS temptc_26225288700,   SUM(generico_5.inversionus) AS sumcalculation_0061002123102817ok,   CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) AS yrfechaok FROM generico_5 WHERE ((generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM')) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) >= 2010) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) <= 2015)) GROUP BY generico_5.anunciante,   yrfechaok,   generico_5.anunciante;
