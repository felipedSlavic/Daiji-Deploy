-- Exclusivamente H2 em memória. Complemento da fixture CP06, sem migração/alteração de produção.
CREATE TABLE MEDICACAO (
 id_medicacao NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_beneficiario NUMBER NOT NULL REFERENCES BENEFICIARIO(id_beneficiario),
 nome_medicamento VARCHAR2(100) NOT NULL, dosagem VARCHAR2(50), horario_previsto VARCHAR2(5)
);
CREATE TABLE CONFIRMACAO_MEDICACAO (
 id_confirmacao NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_medicacao NUMBER NOT NULL REFERENCES MEDICACAO(id_medicacao),
 data_confirmacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 status_confirmacao VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL,
 foto_url VARCHAR2(300),
 CONSTRAINT ck_confirmacao_status CHECK (status_confirmacao IN ('PENDENTE','CONFIRMADO','PERDIDO'))
);
CREATE TABLE CHECKIN (
 id_checkin NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_beneficiario NUMBER NOT NULL REFERENCES BENEFICIARIO(id_beneficiario),
 data_checkin TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 canal VARCHAR2(20) NOT NULL,
 nivel_estresse NUMBER(1), qualidade_sono VARCHAR2(20), qualidade_alimentacao VARCHAR2(30),
 humor VARCHAR2(20), resposta_texto VARCHAR2(500),
 CONSTRAINT ck_checkin_canal CHECK (canal IN ('WHATSAPP','TELEGRAM','APP','SMS')),
 CONSTRAINT ck_checkin_estresse CHECK (nivel_estresse BETWEEN 1 AND 5)
);
INSERT INTO MEDICACAO (id_beneficiario,nome_medicamento,dosagem,horario_previsto) VALUES (1,'Medicamento A','10 mg','08:00');
INSERT INTO MEDICACAO (id_beneficiario,nome_medicamento) VALUES (2,'Medicamento B');
