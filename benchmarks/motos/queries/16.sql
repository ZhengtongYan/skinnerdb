SELECT motos_2.medio AS medio,   SUM(motos_2.inversionus) AS sumcalculation_0061002123102817ok,   CAST(EXTRACT(YEAR FROM motos_2.fecha) AS LONG) AS yrfechaok FROM motos_2 WHERE ((motos_2.medio = 'RADIO') AND (CAST(EXTRACT(YEAR FROM motos_2.fecha) AS LONG) = 2015) AND (motos_2.categoria = 'MOTOCICLETAS')) GROUP BY motos_2.medio,   yrfechaok;