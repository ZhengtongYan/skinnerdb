SELECT SUM(generico_5.inversionus) AS suminversionusok,   CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) AS yrfechaok FROM generico_5 WHERE ((CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) >= 2010) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) <= 2015) AND (generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM'))) GROUP BY yrfechaok;
