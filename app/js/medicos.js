/* =====================================================
   DAIJI · BASE DE ESPECIALIDADES E MÉDICOS
   Usado em descobrir.html e agendar.html
====================================================== */

window.DAIJI_ESPECIALIDADES = {

    'saude-mental': {
        nome: 'Psicologia',
        categoria: 'Saúde Mental',
        icone: 'bi-headset'
    },

    'nutricao': {
        nome: 'Nutrição',
        categoria: 'Nutrição',
        icone: 'bi-egg-fried'
    },

    'clinico-geral': {
        nome: 'Clínico Geral',
        categoria: 'Clínico Geral',
        icone: 'bi-clipboard2-pulse'
    },

    'cardiaca': {
        nome: 'Cardiologia',
        categoria: 'Cardíaca',
        icone: 'bi-heart-pulse'
    },

    'endocrino': {
        nome: 'Endocrinologia',
        categoria: 'Endócrino',
        icone: 'bi-droplet-half'
    },

    'ortopedia': {
        nome: 'Ortopedia',
        categoria: 'Ortopedia',
        icone: 'bi-person-arms-up'
    },

    'dermato': {
        nome: 'Dermatologia',
        categoria: 'Dermato',
        icone: 'bi-bandaid'
    },

    'pediatria': {
        nome: 'Pediatria',
        categoria: 'Pediatria',
        icone: 'bi-emoji-smile'
    }

}


window.DAIJI_MEDICOS = [

    /* ---------- PSICOLOGIA / SAÚDE MENTAL ---------- */
    { id: 'juliana-martins',    especialidade: 'saude-mental', nome: 'Dra. Juliana Martins',    titulo: 'Psicóloga Clínica',          registro: 'CRP 06/123456', nota: 4.9, avaliacoes: 128, duracao: 50, distancia: 1.2 },
    { id: 'rodrigo-tavares',    especialidade: 'saude-mental', nome: 'Dr. Rodrigo Tavares',     titulo: 'Psicólogo · TCC',            registro: 'CRP 06/145872', nota: 4.8, avaliacoes: 94,  duracao: 50, distancia: 2.4 },
    { id: 'beatriz-gomes',      especialidade: 'saude-mental', nome: 'Dra. Beatriz Gomes',      titulo: 'Psiquiatra',                 registro: 'CRM 187654',    nota: 4.9, avaliacoes: 176, duracao: 40, distancia: 3.1 },
    { id: 'lucas-andrade',      especialidade: 'saude-mental', nome: 'Dr. Lucas Andrade',       titulo: 'Psicólogo · Ansiedade',      registro: 'CRP 06/132290', nota: 4.7, avaliacoes: 61,  duracao: 50, distancia: 4.0 },
    { id: 'aline-cavalcanti',   especialidade: 'saude-mental', nome: 'Dra. Aline Cavalcanti',   titulo: 'Psicóloga Infantojuvenil',   registro: 'CRP 06/158813', nota: 4.8, avaliacoes: 83,  duracao: 50, distancia: 5.6 },

    /* ---------- NUTRIÇÃO ---------- */
    { id: 'fernando-azevedo',   especialidade: 'nutricao', nome: 'Dr. Fernando Azevedo',   titulo: 'Nutricionista Clínico',      registro: 'CRN 3/12345', nota: 4.8, avaliacoes: 96,  duracao: 40, distancia: 0.8 },
    { id: 'gabriela-lima',      especialidade: 'nutricao', nome: 'Dra. Gabriela Lima',     titulo: 'Nutricionista Esportiva',    registro: 'CRN 3/28761', nota: 4.9, avaliacoes: 142, duracao: 40, distancia: 1.9 },
    { id: 'vitor-sampaio',      especialidade: 'nutricao', nome: 'Dr. Vitor Sampaio',      titulo: 'Nutrólogo',                  registro: 'CRM 201334',  nota: 4.7, avaliacoes: 58,  duracao: 45, distancia: 2.7 },
    { id: 'priscila-moraes',    especialidade: 'nutricao', nome: 'Dra. Priscila Moraes',   titulo: 'Nutricionista Funcional',    registro: 'CRN 3/31905', nota: 4.8, avaliacoes: 77,  duracao: 40, distancia: 3.8 },
    { id: 'henrique-bastos',    especialidade: 'nutricao', nome: 'Dr. Henrique Bastos',    titulo: 'Nutricionista · Diabetes',   registro: 'CRN 3/22418', nota: 4.6, avaliacoes: 49,  duracao: 40, distancia: 6.2 },

    /* ---------- CLÍNICO GERAL ---------- */
    { id: 'camila-ribeiro',     especialidade: 'clinico-geral', nome: 'Dra. Camila Ribeiro',    titulo: 'Clínica Geral',              registro: 'CRM 234567', nota: 4.9, avaliacoes: 210, duracao: 30, distancia: 0.6 },
    { id: 'andre-figueiredo',   especialidade: 'clinico-geral', nome: 'Dr. André Figueiredo',   titulo: 'Clínico Geral',              registro: 'CRM 219083', nota: 4.8, avaliacoes: 167, duracao: 30, distancia: 1.4 },
    { id: 'sofia-mendes',       especialidade: 'clinico-geral', nome: 'Dra. Sofia Mendes',      titulo: 'Medicina de Família',        registro: 'CRM 245190', nota: 4.9, avaliacoes: 133, duracao: 30, distancia: 2.2 },
    { id: 'paulo-rezende',      especialidade: 'clinico-geral', nome: 'Dr. Paulo Rezende',      titulo: 'Clínico Geral · Geriatria',  registro: 'CRM 176402', nota: 4.7, avaliacoes: 88,  duracao: 35, distancia: 3.5 },
    { id: 'tatiana-borges',     especialidade: 'clinico-geral', nome: 'Dra. Tatiana Borges',    titulo: 'Clínica Geral',              registro: 'CRM 228871', nota: 4.8, avaliacoes: 102, duracao: 30, distancia: 4.7 },

    /* ---------- CARDIOLOGIA ---------- */
    { id: 'eduardo-lins',       especialidade: 'cardiaca', nome: 'Dr. Eduardo Lins',       titulo: 'Cardiologista',              registro: 'CRM 345678', nota: 4.7, avaliacoes: 154, duracao: 45, distancia: 1.1 },
    { id: 'mariana-couto',      especialidade: 'cardiaca', nome: 'Dra. Mariana Couto',     titulo: 'Cardiologista',              registro: 'CRM 312459', nota: 4.9, avaliacoes: 121, duracao: 45, distancia: 2.0 },
    { id: 'ricardo-pimentel',   especialidade: 'cardiaca', nome: 'Dr. Ricardo Pimentel',   titulo: 'Cardiologista · Arritmia',   registro: 'CRM 298310', nota: 4.8, avaliacoes: 97,  duracao: 45, distancia: 3.3 },
    { id: 'luciana-farias',     especialidade: 'cardiaca', nome: 'Dra. Luciana Farias',    titulo: 'Cardiologista · Hipertensão',registro: 'CRM 330762', nota: 4.8, avaliacoes: 86,  duracao: 45, distancia: 4.4 },
    { id: 'gustavo-melo',       especialidade: 'cardiaca', nome: 'Dr. Gustavo Melo',       titulo: 'Cardiologista Esportivo',    registro: 'CRM 355104', nota: 4.6, avaliacoes: 64,  duracao: 40, distancia: 5.9 },

    /* ---------- ENDOCRINOLOGIA ---------- */
    { id: 'renata-oliveira',    especialidade: 'endocrino', nome: 'Dra. Renata Oliveira',   titulo: 'Endocrinologista',           registro: 'CRM 456789', nota: 4.9, avaliacoes: 87,  duracao: 40, distancia: 1.7 },
    { id: 'felipe-arantes',     especialidade: 'endocrino', nome: 'Dr. Felipe Arantes',     titulo: 'Endocrinologista · Diabetes',registro: 'CRM 432115', nota: 4.8, avaliacoes: 112, duracao: 40, distancia: 2.5 },
    { id: 'isabela-quintana',   especialidade: 'endocrino', nome: 'Dra. Isabela Quintana',  titulo: 'Endocrinologista · Tireoide',registro: 'CRM 468230', nota: 4.9, avaliacoes: 79,  duracao: 40, distancia: 3.6 },
    { id: 'marcos-teixeira',    especialidade: 'endocrino', nome: 'Dr. Marcos Teixeira',    titulo: 'Endocrinologista',           registro: 'CRM 410987', nota: 4.7, avaliacoes: 53,  duracao: 40, distancia: 4.9 },
    { id: 'carolina-duarte',    especialidade: 'endocrino', nome: 'Dra. Carolina Duarte',   titulo: 'Endocrinologista · Obesidade',registro: 'CRM 475506', nota: 4.8, avaliacoes: 68, duracao: 40, distancia: 6.8 },

    /* ---------- ORTOPEDIA ---------- */
    { id: 'bruno-costa',        especialidade: 'ortopedia', nome: 'Dr. Bruno Costa',        titulo: 'Ortopedista',                registro: 'CRM 567890', nota: 4.6, avaliacoes: 73,  duracao: 35, distancia: 1.5 },
    { id: 'leticia-souza',      especialidade: 'ortopedia', nome: 'Dra. Letícia Souza',     titulo: 'Ortopedista · Joelho',       registro: 'CRM 541276', nota: 4.8, avaliacoes: 91,  duracao: 35, distancia: 2.3 },
    { id: 'daniel-rocha',       especialidade: 'ortopedia', nome: 'Dr. Daniel Rocha',       titulo: 'Ortopedista · Coluna',       registro: 'CRM 529843', nota: 4.7, avaliacoes: 118, duracao: 35, distancia: 3.0 },
    { id: 'amanda-freitas',     especialidade: 'ortopedia', nome: 'Dra. Amanda Freitas',    titulo: 'Medicina Esportiva',         registro: 'CRM 578412', nota: 4.9, avaliacoes: 66,  duracao: 35, distancia: 4.2 },
    { id: 'otavio-pereira',     especialidade: 'ortopedia', nome: 'Dr. Otávio Pereira',     titulo: 'Ortopedista · Ombro',        registro: 'CRM 553901', nota: 4.7, avaliacoes: 57,  duracao: 35, distancia: 5.4 },

    /* ---------- DERMATOLOGIA ---------- */
    { id: 'helena-duarte',      especialidade: 'dermato', nome: 'Dra. Helena Duarte',     titulo: 'Dermatologista',             registro: 'CRM 678901', nota: 4.8, avaliacoes: 112, duracao: 30, distancia: 0.9 },
    { id: 'rafaela-nascimento', especialidade: 'dermato', nome: 'Dra. Rafaela Nascimento',titulo: 'Dermatologista · Acne',      registro: 'CRM 654327', nota: 4.9, avaliacoes: 148, duracao: 30, distancia: 2.1 },
    { id: 'thiago-brandao',     especialidade: 'dermato', nome: 'Dr. Thiago Brandão',     titulo: 'Dermatologista',             registro: 'CRM 689115', nota: 4.7, avaliacoes: 72,  duracao: 30, distancia: 3.4 },
    { id: 'vanessa-coelho',     especialidade: 'dermato', nome: 'Dra. Vanessa Coelho',    titulo: 'Dermatologista · Cabelos',   registro: 'CRM 661480', nota: 4.8, avaliacoes: 89,  duracao: 30, distancia: 4.6 },
    { id: 'leonardo-dias',      especialidade: 'dermato', nome: 'Dr. Leonardo Dias',      titulo: 'Dermatologista Pediátrico',  registro: 'CRM 697362', nota: 4.6, avaliacoes: 41,  duracao: 30, distancia: 6.0 },

    /* ---------- PEDIATRIA ---------- */
    { id: 'marcelo-nunes',      especialidade: 'pediatria', nome: 'Dr. Marcelo Nunes',      titulo: 'Pediatra',                   registro: 'CRM 789012', nota: 4.9, avaliacoes: 140, duracao: 30, distancia: 1.3 },
    { id: 'fernanda-castro',    especialidade: 'pediatria', nome: 'Dra. Fernanda Castro',   titulo: 'Pediatra · Neonatologia',    registro: 'CRM 762594', nota: 4.9, avaliacoes: 187, duracao: 30, distancia: 2.0 },
    { id: 'joao-vasconcelos',   especialidade: 'pediatria', nome: 'Dr. João Vasconcelos',   titulo: 'Pediatra',                   registro: 'CRM 771038', nota: 4.8, avaliacoes: 99,  duracao: 30, distancia: 2.9 },
    { id: 'natalia-barros',     especialidade: 'pediatria', nome: 'Dra. Natália Barros',    titulo: 'Pediatra · Alergia',         registro: 'CRM 745821', nota: 4.7, avaliacoes: 64,  duracao: 30, distancia: 4.1 },
    { id: 'caio-montenegro',    especialidade: 'pediatria', nome: 'Dr. Caio Montenegro',    titulo: 'Hebiatra (adolescentes)',    registro: 'CRM 798456', nota: 4.8, avaliacoes: 52,  duracao: 30, distancia: 5.3 }

]


/* helpers */

window.DAIJI_medicoPorId = function (id) {
    return window.DAIJI_MEDICOS.find(m => m.id === id) || null
}

window.DAIJI_medicosDa = function (especialidade) {
    return window.DAIJI_MEDICOS.filter(m => m.especialidade === especialidade)
}

window.DAIJI_iniciais = function (nome) {
    return nome
        .replace(/^Dr[a]?\.\s*/, '')
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map(p => p[0])
        .join('')
        .toUpperCase()
}
