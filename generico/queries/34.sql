SELECT CAST(EXTRACT(MONTH FROM generico_5.fecha) AS LONG) AS mnfechaok,   SUM(generico_5.inversionus) AS sumcalculation_0061002123102817ok,   CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) AS yrfechaok FROM generico_5 WHERE ((CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) IN (2014, 2015)) AND (generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM'))) GROUP BY mnfechaok,   yrfechaok;
