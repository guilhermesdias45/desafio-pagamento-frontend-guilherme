-- Script executado automaticamente pelo PostgreSQL na primeira inicialização
-- Cria um banco isolado para cada microserviço (isolamento de dados — nenhum serviço acessa banco de outro)

CREATE DATABASE user_db;
CREATE DATABASE payment_db;
CREATE DATABASE order_db;
CREATE DATABASE notification_db;
CREATE DATABASE fraud_db;
