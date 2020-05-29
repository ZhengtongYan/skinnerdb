SELECT generico_5.number_of_records FROM generico_5 WHERE ((CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) >= 2010) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) <= 2015) AND (generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM')) AND (CAST(EXTRACT(MONTH FROM generico_5.fecha) AS LONG) IN (1, 2, 3, 4, 5)));
