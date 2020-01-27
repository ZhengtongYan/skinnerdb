SELECT MIN(uberlandia_1.data_de_inicio) AS tempattrdata_de_iniciook39820783870,   MAX(uberlandia_1.data_de_inicio) AS tempattrdata_de_iniciook867823740,   MIN(uberlandia_1.data_de_previsaode_termino) AS tempattrdata_de_previsaode_terminook41718527080,   MAX(uberlandia_1.data_de_previsaode_termino) AS tempattrdata_de_previsaode_terminook5701031440,   CAST(uberlandia_1.codigo_da_oferta AS LONG) AS codigo_da_oferta,   uberlandia_1.edicao_catalogo_guia AS edicao_catalogo_guia,   uberlandia_1.nome_do_curso AS nome_do_curso,   uberlandia_1.situacao_da_turma AS situacao_da_turma,   uberlandia_1.subtipo_curso AS subtipo_curso,   SUM(CAST(uberlandia_1.number_of_records AS LONG)) AS sumnumber_of_recordsok,   CAST(EXTRACT(YEAR FROM uberlandia_1.data_de_inicio) AS LONG) AS yrdata_de_iniciook FROM uberlandia_1 WHERE ((uberlandia_1.nome_do_curso IN ('Agente de Sade e Bem estar Animal', 'Eletricista de Veculos de Transporte de Cargas e de Passageiros', 'Recepcionista em Meios de Hospedagem', 'RECEPCIONISTA EM MEIOS DE HOSPEDAGEM', 'Soldador no Processo MIG/MAG', 'SOLDADOR NO PROCESSO MIG/MAG', 'Traador de Caldeiraria', 'TRAADOR DE CALDEIRARIA')) AND (NOT (uberlandia_1.situacao_da_turma IN ('CANCELADA', 'CRIADA', 'PUBLICADA'))) AND (uberlandia_1.subtipo_curso = 'FIC') AND (CAST(EXTRACT(YEAR FROM uberlandia_1.data_de_inicio) AS LONG) = 2015) AND (NOT ((uberlandia_1.nome_da_sit_matricula_situacao_detalhada NOT IN ('', 'TRANSF_EXT', 'INTEGRALIZADA', 'FREQ_INIC_INSUF', 'TRANCADA', 'CONCLUDA', 'TRANSF_INT', 'EM_CURSO', 'REPROVADA', 'ABANDONO', 'CONFIRMADA', 'EM_DEPENDNCIA')) OR (uberlandia_1.nome_da_sit_matricula_situacao_detalhada IS NULL)))) GROUP BY uberlandia_1.codigo_da_oferta,   edicao_catalogo_guia,   nome_do_curso,   situacao_da_turma,   subtipo_curso,   yrdata_de_iniciook,   codigo_da_oferta;
