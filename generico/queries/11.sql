SELECT generico_5.anunciante AS datos_copia,   SUM(generico_5.inversionus) AS temptc_26225288700,   generico_5.vehiculo AS vehiculo,   SUM(generico_5.inversionus) AS sumcalculation_0061002123102817ok,   SUM(CAST(generico_5.numanuncios AS LONG)) AS sumnumanunciosok,   CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) AS yrfechaok FROM generico_5 WHERE ((generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM')) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) IN (2014, 2015)) AND (generico_5.medio = 'RADIO') AND (CAST(EXTRACT(MONTH FROM generico_5.fecha) AS LONG) IN (1, 2, 3, 4, 5))) GROUP BY generico_5.anunciante,   generico_5.vehiculo,   yrfechaok;
