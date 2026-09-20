-- Fixture de testes H2 MODE=Oracle, somente em memória.
-- Subconjunto das cinco tabelas do script oficial, não é migração nem script de produção.
CREATE TABLE EMPRESA (
 id_empresa NUMBER GENERATED ALWAYS AS IDENTITY,
 cnpj VARCHAR2(14) NOT NULL, razao_social VARCHAR2(150) NOT NULL,
 data_cadastro DATE DEFAULT SYSDATE NOT NULL,
 CONSTRAINT pk_empresa PRIMARY KEY (id_empresa), CONSTRAINT uk_empresa_cnpj UNIQUE (cnpj)
);
CREATE TABLE BENEFICIARIO (
 id_beneficiario NUMBER GENERATED ALWAYS AS IDENTITY, id_empresa NUMBER NOT NULL,
 nome VARCHAR2(150) NOT NULL, cpf VARCHAR2(11) NOT NULL, email VARCHAR2(150),
 data_nascimento DATE NOT NULL, telefone_whatsapp VARCHAR2(20) NOT NULL,
 status_ativo CHAR(1) DEFAULT 'S' NOT NULL, data_cadastro DATE DEFAULT SYSDATE NOT NULL,
 CONSTRAINT pk_beneficiario PRIMARY KEY (id_beneficiario),
 CONSTRAINT uk_beneficiario_cpf UNIQUE (cpf), CONSTRAINT uk_beneficiario_email UNIQUE (email),
 CONSTRAINT ck_beneficiario_status CHECK (status_ativo IN ('S','N')),
 CONSTRAINT fk_beneficiario_empresa FOREIGN KEY (id_empresa) REFERENCES EMPRESA(id_empresa)
);
CREATE TABLE PROFISSIONAL_SAUDE (
 id_profissional NUMBER GENERATED ALWAYS AS IDENTITY, nome VARCHAR2(150) NOT NULL,
 crm VARCHAR2(20) NOT NULL, especialidade VARCHAR2(50) NOT NULL,
 CONSTRAINT pk_profissional_saude PRIMARY KEY (id_profissional),
 CONSTRAINT uk_profissional_crm UNIQUE (crm)
);
CREATE TABLE SCORE_RISCO_RENAL (
 id_score NUMBER GENERATED ALWAYS AS IDENTITY, id_beneficiario NUMBER NOT NULL,
 data_calculo DATE NOT NULL, valor_score NUMBER(5,2) NOT NULL,
 classificacao_risco VARCHAR2(10) NOT NULL,
 CONSTRAINT pk_score_risco_renal PRIMARY KEY (id_score),
 CONSTRAINT ck_score_classificacao CHECK (classificacao_risco IN ('BAIXO','MEDIO','ALTO')),
 CONSTRAINT fk_score_beneficiario FOREIGN KEY (id_beneficiario) REFERENCES BENEFICIARIO(id_beneficiario)
);
CREATE TABLE ENCAMINHAMENTO (
 id_encaminhamento NUMBER GENERATED ALWAYS AS IDENTITY, id_beneficiario NUMBER NOT NULL,
 id_profissional NUMBER NOT NULL, data_encaminhamento DATE DEFAULT SYSDATE NOT NULL,
 data_consulta DATE, status_encaminhamento VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL,
 CONSTRAINT pk_encaminhamento PRIMARY KEY (id_encaminhamento),
 CONSTRAINT ck_encaminhamento_status CHECK (status_encaminhamento IN ('PENDENTE','AGENDADO','REALIZADO','CANCELADO')),
 CONSTRAINT fk_encaminhamento_beneficiario FOREIGN KEY (id_beneficiario) REFERENCES BENEFICIARIO(id_beneficiario),
 CONSTRAINT fk_encaminhamento_profissional FOREIGN KEY (id_profissional) REFERENCES PROFISSIONAL_SAUDE(id_profissional)
);
INSERT INTO EMPRESA (cnpj, razao_social) VALUES ('11111111111111', 'Empresa 1');
INSERT INTO EMPRESA (cnpj, razao_social) VALUES ('22222222222222', 'Empresa 2');
INSERT INTO EMPRESA (cnpj, razao_social) VALUES ('33333333333333', 'Empresa vazia');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,email,data_nascimento,telefone_whatsapp) VALUES (1,'Rafael Almeida','11111111111','rafael@daiji.com',DATE '1980-01-02','11900000001');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,email,data_nascimento,telefone_whatsapp) VALUES (1,'Maria Silva','22222222222','maria@daiji.com',DATE '1980-01-02','11900000002');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,data_nascimento,telefone_whatsapp) VALUES (1,'Bruno','33333333333',DATE '1980-01-02','11900000003');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,data_nascimento,telefone_whatsapp) VALUES (1,'Sem score','44444444444',DATE '1980-01-02','11900000004');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,data_nascimento,telefone_whatsapp) VALUES (2,'Alto empresa 2','55555555555',DATE '1980-01-02','11900000005');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,data_nascimento,telefone_whatsapp) VALUES (2,'Baixo empresa 2','66666666666',DATE '1980-01-02','11900000006');
INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,data_nascimento,telefone_whatsapp,status_ativo) VALUES (2,'Inativo sem score','77777777777',DATE '1980-01-02','11900000007','N');
INSERT INTO PROFISSIONAL_SAUDE (nome,crm,especialidade) VALUES ('Dra. Zelia','CRM-1','Nefrologia');
INSERT INTO PROFISSIONAL_SAUDE (nome,crm,especialidade) VALUES ('Dra. Ana Souza','CRM-2','Nefrologia');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (1,TIMESTAMP '2026-09-16 12:30:45',97,'BAIXO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (1,TIMESTAMP '2026-09-15 12:30:45',10,'ALTO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (2,TIMESTAMP '2026-09-15 12:30:45',90,'BAIXO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (2,TIMESTAMP '2026-09-16 12:30:45',30,'ALTO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (2,TIMESTAMP '2026-09-16 12:30:45',25,'ALTO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (3,TIMESTAMP '2026-09-16 12:30:45',60,'MEDIO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (5,TIMESTAMP '2026-09-16 12:30:45',20,'ALTO');
INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco) VALUES (6,TIMESTAMP '2026-09-16 12:30:45',80,'BAIXO');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento) VALUES (2,2,TIMESTAMP '2026-09-16 10:00:00');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento) VALUES (2,1,TIMESTAMP '2026-09-16 10:00:00');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento,data_consulta,status_encaminhamento) VALUES (1,2,TIMESTAMP '2026-09-16 10:00:00',TIMESTAMP '2026-09-20 14:00:00','AGENDADO');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento,status_encaminhamento) VALUES (3,2,TIMESTAMP '2026-09-16 10:00:00','REALIZADO');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento,status_encaminhamento) VALUES (5,2,TIMESTAMP '2026-09-16 10:00:00','CANCELADO');
INSERT INTO ENCAMINHAMENTO (id_beneficiario,id_profissional,data_encaminhamento) VALUES (7,2,TIMESTAMP '2026-09-16 10:00:00');

