SELECT (CASE WHEN (generico_5.medio IN ('PRENSA', 'REVISTAS', 'REVISTAS DE PRENSA')) THEN 'PRENSA' ELSE generico_5.medio END) AS medio_grupo FROM generico_5 WHERE ((generico_5.anunciante IN ('BANTRAB/TODOTICKET', 'TODOTICKET', 'TODOTICKET.COM')) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) >= 2010) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) <= 2015) AND (CAST(EXTRACT(YEAR FROM generico_5.fecha) AS LONG) = 2015)) GROUP BY medio_grupo;