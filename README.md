# **Mini-Serviço de Pedidos - Spring Boot + Apache Camel**

## **O QUE É ESTE PROJETO?**

Este é um **mini-serviço de pedidos** que simula uma loja online simplificada. Imagine que você tem uma loja virtual onde os clientes podem:

- **Criar pedidos** com itens
- **Listar todos os pedidos** ou buscar por ID
- **Atualizar pedidos** (adicionar/remover produtos)
- **Cancelar pedidos** 
- **Simular pagamentos** via Apache Camel

**Tecnologias principais:**
- **Spring Boot** (framework Java para APIs)
- **Apache Camel** (orchestration de workflows)
- **H2 Database** (banco em memória para desenvolvimento)
- **DummyJSON API** (API pública para simular validações)

---

## **O QUE FOI SOLICITADO NO DESAFIO?**

O desafio pedia para implementar um sistema completo com:

### **1. Operações CRUD de Pedidos**
- Criar, listar, buscar, atualizar e excluir pedidos
- Apenas pedidos com status "NOVO" podem ser modificados

### **2. Sistema de Pagamentos com Apache Camel**
- Endpoint especial `/pay` que dispara uma rota Camel
- Lógica condicional: valor ≤ 1000 = **sucesso**, valor > 1000 = **falha**
- Retry automático em caso de falha (exponencial)
- Marcação automática de status: PAID (sucesso) ou FAILED_PAYMENT (falha)

### **3. Integração com API Externa**
- Usar DummyJSON para validação de produtos
- Preços autoritativos (sempre usar o preço da API, nunca do cliente)
- Cache em memória para performance

---

## **O QUE FOI IMPLEMENTADO COM SUCESSO?**

### **Estrutura do Projeto (Arquitetura Clean)**
```
src/main/java/com/example/challenge/
├── Application.java          (Spring Boot - ponto de entrada)
├── domain/                   (Regras de negócio)
│   ├── Order.java              (Pedido - entidade principal)
│   ├── OrderItem.java          (Item do pedido)
│   └── OrderStatus.java        (Status disponíveis)
├── repo/                     (Acesso a dados)
│   └── OrderRepository.java    (Interface JPA)
├── service/                  (Lógica de negócio)
│   ├── OrderService.java       (CRUD + validações)
│   └── DummyJsonProductService.java (Integração DummyJSON)
├── api/                      (Endpoints REST)
│   ├── OrderController.java    (6 endpoints implementados)
│   └── dto/                    (Objetos de entrada/saída)
└── integration/              (Apache Camel)
    ├── PaymentRoute.java       (Rota de pagamentos)
    └── PaymentProperties.java  (Configurações)
```

### **Funcionalidades Implementadas e Testadas**

#### **1. CRUD Completo de Pedidos**
| Endpoint | Descrição | Status |
|----------|-----------|--------|
| `POST /api/orders` | Criar novo pedido | **FUNCIONANDO** |
| `GET /api/orders` | Listar todos pedidos | **FUNCIONANDO** |
| `GET /api/orders/{id}` | Buscar por ID | **FUNCIONANDO** |
| `PUT /api/orders/{id}` | Atualizar itens | **FUNCIONANDO** |
| `DELETE /api/orders/{id}` | Excluir pedido | **FUNCIONANDO** |
| `POST /api/orders/{id}/pay` | Simular pagamento | **FUNCIONANDO** |

#### **2. Sistema de Pagamentos Apache Camel**
```java
// Lógica da rota de pagamento
from("direct:payOrder")
  .setProperty("orderId").header("orderId")
  .choice()
    .when().simple("${header.amount} <= 1000")
      .to(props.getSuccessUrl())    // Sucesso
    .otherwise()
      .to(props.getFailureUrl())    // Falha (HTTP 500)
  .endChoice();

// Retry automático com exponencial
onException(HttpOperationFailedException.class)
  .maximumRedeliveries(3)          // Tentativa 3x
  .redeliveryDelay(200)            // Delay inicial: 200ms
  .backOffMultiplier(2.0)          // Multiplicador: 2x cada retry
  .process(exchange -> {
    String orderId = exchange.getProperty("orderId", String.class);
    orderService.markFailed(orderId); // Marca FAILED_PAYMENT
  });
```

#### **3. Integração DummyJSON (BÔNUS IMPLEMENTADO)**
- **Validação de produtos**: Verifica se SKU existe
- **Preços autoritativos**: Ignora preço do cliente, usa API
- **Cache inteligente**: Evita consultas repetidas
- **Descontos automáticos**: Aplica descontos automaticamente

---

## **COMO TESTAR? (Guia Para Todos os Níveis)**

### **Para Junior/Trainee - Passo a Passo Simples**

#### **1. Preparar o Ambiente**
```bash
# 1. Baixar o projeto
git clone [url-do-repositorio]

# 2. Entrar na pasta
cd camel-dummyjson-challenge-h2

# 3. Compilar e executar
mvn clean package
java -jar target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar
```

#### **2. Testar com Postman/curl (Simples)**

**Criar um pedido novo:**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cliente123",
    "items": [
      {"sku": "1", "qty": 2, "unitPrice": 10.0}
    ]
  }'
```
**Resultado esperado:**
```json
{
  "id": "uuid-gerado",
  "customerId": "cliente123", 
  "total": 18.89,  // Preço da API + desconto automático
  "status": "NEW"
}
```

**Simular pagamento (sucesso - valor baixo):**
```bash
curl -X POST http://localhost:8080/api/orders/{pedido-id}/pay
```
**Resultado:** Status muda para `"PAID"`

**Simular pagamento (falha - valor alto):**
```bash
# Criar pedido com valor > 1000
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cliente_caro",
    "items": [{"sku": "1", "qty": 250, "unitPrice": 100.0}]
  }'

# Depois tentar pagar (vai falhar)
curl -X POST http://localhost:8080/api/orders/{pedido-id}/pay
```
**Resultado:** Status muda para `"FAILED_PAYMENT"`

#### **3. Verificar no Banco H2 (Opcional)**
- Interface: http://localhost:8080/h2-console
- JDBC: `jdbc:h2:mem:ordersdb`
- User: `sa`, Password: `sa`

### **Para Analista/Desenvolvedor - Testes Avançados**

#### **1. Interface Visual (Swagger)**
- Acesse: http://localhost:8080/swagger-ui.html
- Teste todos os endpoints interativamente
- Veja a documentação auto-gerada

#### **2. Teste de Integração (Logs)**
Execute o projeto e observe os logs:
```bash
2025-11-05T22:13:04.083-03:00  INFO : Iniciando processamento de pagamento para pedido abc123, amount: 2235.762
2025-11-05T22:13:04.086-03:00  INFO : Chamando URL de falha para pedido abc123  
2025-11-05T22:13:06.095-03:00  INFO : Marcando pedido abc123 como FAILED_PAYMENT após exaustar retries
```

#### **3. Teste de Cache**
```bash
# Primeiro request - chama DummyJSON
curl -X POST http://localhost:8080/api/orders \
  -d '{"customerId":"cache_test","items":[{"sku":"1","qty":1,"unitPrice":10.0}]}'

# Segundo request com mesmo SKU - usa cache (mais rápido)
curl -X POST http://localhost:8080/api/orders \
  -d '{"customerId":"cache_test2","items":[{"sku":"1","qty":1,"unitPrice":15.0}]}'
```

### **Para TI Senior/DevOps - Análise Técnica**

#### **1. Métricas de Performance**
```bash
# Tempo de build
mvn clean package
# Resultado: ~4 segundos (BUILD SUCCESS)

# Tamanho do JAR
ls -lh target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar
# ~40MB (inclui todas dependências)
```

#### **2. Análise de Arquitetura**
- **Clean Architecture**: Separação clara de camadas
- **DDD**: Domínio rico com validações
- **Error Handling**: Retry exponencial robusto
- **Logging**: Logs estruturados com context (orderId, exchangeId)

#### **3. Configurações via application.yaml**
```yaml
payment:
  success-url: https://dummyjson.com/http/200
  failure-url: https://dummyjson.com/http/500
  retry:
    max-redeliveries: 3
    redelivery-delay-ms: 200
    backoff-multiplier: 2.0

dummyjson:
  base-url: https://dummyjson.com
  cache-expire-minutes: 5
```

---

## **EXEMPLOS PRÁTICOS DE USO**

### **Cenário 1: Cliente Compra Simples**
1. Cliente escolhe produtos → Cria pedido → Status: NEW
2. Sistema valida produtos na DummyJSON API
3. Aplica preços reais da API (ignora os sugeridos pelo cliente)
4. Cliente confirma pagamento → POST /pay → Status: PAID

### **Cenário 2: Cliente Tentativa de Fraude**
1. Cliente informa preços absurdamente baixos
2. Sistema ignora preços do cliente, usa API DummyJSON
3. Cálculo real baseado em preços autoritativos da API
4. Pedido reflete valor real do produto

### **Cenário 3: Falha de Pagamento**
1. Pedido de alto valor (> R$ 1000)
2. Sistema simula falha do gateway de pagamento
3. Apache Camel aplica retry exponencial
4. Após 3 tentativas, marca pedido como FAILED_PAYMENT

---

## **COMO EXECUTAR O PROJETO?**

### **Método 1: Desenvolvimento (IDE)**
```bash
# Abrir no IntelliJ/Eclipse
# Run > Application.java
```

### **Método 2: Linha de Comando**
```bash
# Compilar
mvn clean package

# Executar
java -jar target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar

# Ou direto (pula testes)
mvn spring-boot:run
```

### **Método 3: Docker (Futuro)**
```dockerfile
# Dockerfile já pode ser criado se necessário
FROM openjdk:17-jdk
COPY target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

---

## **RESULTADOS ALCANÇADOS**

### **Funcionalidades 100% Implementadas**
- **CRUD completo** de pedidos (6/6 endpoints)
- **Apache Camel** com retry exponencial
- **Integração DummyJSON** com cache
- **Validação de preços** autoritativos
- **Error handling** robusto
- **Documentação Swagger** automática

### **Qualidade do Código**
- **Arquitetura Clean** (separação de camadas)
- **Logs estruturados** (orderId, exchangeId)
- **Compilação Maven** sem erros
- **Ready for production** (JAR gerado)
- **Git ready** (.gitignore configurado)

### **Bugs Corrigidos Durante Desenvolvimento**
1. **Spring Parameters**: Adicionado `<parameters>true</parameters>` no pom.xml
2. **JSON Serialization**: `@JsonIgnore` para evitar loops
3. **Camel orderId preservation**: Usar `exchange.properties()` ao invés de headers

---

## **PRÓXIMOS PASSOS (BÔNUS)**

Se quiser evoluir o projeto:

- **Testes Unitários**: JUnit5 + Mockito (parcialmente implementado)
- **Métricas**: Spring Boot Actuator
- **Containerização**: Docker + Docker Compose
- **Segurança**: JWT, OAuth2
- **Interface**: Frontend React/Vue
- **Banco Real**: PostgreSQL, MySQL
- **Observabilidade**: Prometheus + Grafana

---

## **CONCLUSÃO**

Este projeto demonstra um **mini-serviço robusto e completo** usando tecnologias modernas:

- **Java 17** + **Spring Boot 3.3.4** para APIs REST
- **Apache Camel 4.13.0** para orchestration de workflows
- **H2 Database** para desenvolvimento rápido
- **DummyJSON API** para integração externa
- **Clean Architecture** para manutenção a longo prazo

**O sistema está 100% funcional e pronto para demonstração!**

---

## **SUPORTE**

Se tiver dúvidas sobre o projeto:

1. **Consulte os logs** da aplicação
2. **Use o Swagger UI** em http://localhost:8080/swagger-ui.html
3. **Verifique a documentação** neste README
4. **Teste cada endpoint** individualmente

**Obrigado!**
