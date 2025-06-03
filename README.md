# Documentação Tronos

## 1. Visão Geral

**Estilo de combate:** Robô adaptativo com foco em mira inteligente (GuessFactor), movimentação evasiva (anti-gravidade) e radar com rastreamento constante.

**Objetivo:** Criar um robô que seja capaz de aprender o comportamento do inimigo, adaptar sua mira com base em histórico de movimentos e evitar ataques através de movimentação autônoma baseada em forças.

## 2. Arquitetura Geral

* **Radar:** Permanente e travado no alvo (Lock-on Radar com overshoot)
* **Mira:** GuessFactor Targeting com histogramas de aprendizado
* **Movimentação:** Anti-Gravity Movement
* **Gerenciamento de energia:** Ajuste dinâmico da potência de fogo
* **Evasão:** Desvios laterais ao ser atingido por projéteis

## 3. Componentes do Robô

### 3.1 Classe Principal: `Tronos`

Herdeira de `AdvancedRobot`, é o núcleo do robô. Controla comportamento geral, radar, mira, movimentação e interação com eventos do Robocode.

### Atributos Globais:

```java
private Map<String, EnemyBot> enemies = new HashMap<>();
private static final double MAX_FIRE_POWER = 3.0;
private static final double MIN_FIRE_POWER = 0.1;
```

* `enemies`: estrutura que armazena os dados de cada inimigo escaneado
* `MAX_FIRE_POWER`, `MIN_FIRE_POWER`: limites da potência de tiro para controle dinâmico de energia

### 3.2 Método `run()`

Inicializa o robô:

* Define cores
* Configura acoplamentos entre robô, radar e canhão
* Gira radar 360º
* Loop principal: atualiza todas as "ondas" ativas dos inimigos

### 3.3 Método `onScannedRobot()`

Executado ao escanear qualquer robô inimigo:

#### Ações principais:

1. Atualiza dados do inimigo (`EnemyBot`)
2. Trava o radar no inimigo
3. Seleciona o inimigo mais próximo para ataque
4. Executa mira com GuessFactor se aplicável
5. Executa movimento anti-gravidade

#### Mira (GuessFactor):

* Verifica se inimigo atirou recentemente (queda de energia)
* Se sim, prioriza movimento evasivo
* Se não, ajusta potência e calcula o ângulo de tiro com base nos histogramas

#### Movimento (Anti-Gravity):

* Calcula vetores de força repulsiva dos inimigos
* Soma vetores de atração para o centro da arena
* Gera ângulo de movimento final a ser seguido

### 3.4 Eventos

* `onHitByBullet`: Move lateralmente ao levar dano
* `onHitWall`: Garante que não fique preso na parede
* `onRobotDeath`: Remove robô morto do mapa de inimigos

### 3.5 Métodos Utilitários

#### `absoluteBearing(double x1, double y1, double x2, double y2)`

Calcula o ângulo absoluto entre dois pontos.

#### `normalizeBearing(double angle)`

Normaliza um ângulo para o intervalo \[-180, 180].

#### `calcMoveAngle()`

Executa cálculo anti-gravidade:

* Itera sobre todos os inimigos
* Calcula força de repulsão proporcional à distância
* Adiciona uma força atrativa ao centro da arena
* Retorna o ângulo do vetor resultante

## 4. Classe Auxiliar: `EnemyBot`

Armazena e atualiza os dados de cada inimigo:

### Atributos:

* Informativos: nome, distância, posição, energia, velocidade, etc.
* `guessFactors[31]`: histograma para GuessFactor Targeting
* `waves`: lista de "ondas" de tiro em análise

### Métodos:

#### `update(ScannedRobotEvent e, AdvancedRobot robot)`

Atualiza os dados do inimigo com base em um escaneamento.

#### `registerWave(Wave w)`

Adiciona uma nova onda de tiro (disparo registrado).

#### `updateWaves(long currentTime, AdvancedRobot robot)`

Verifica se as ondas chegaram ao destino esperado e atualiza o histograma `guessFactors`.

#### `getBestGuessFactor()`

Retorna o índice mais "popular" no histograma, ou seja, onde o inimigo costuma estar quando é alvejado.

#### `maxEscapeAngle(double bulletSpeed)`

Calcula o ângulo máximo que o inimigo pode escapar dado a velocidade da bala.

#### `direction()`

Determina lateralidade do movimento do inimigo.

#### `normalizeBearingRad(double ang)`

Normaliza um ângulo em radianos para o intervalo \[-PI, PI]

## 5. Classe Auxiliar: `Wave`

Representa uma "onda de tiro" disparada, utilizada para rastrear movimento do inimigo e aprender padrões.

### Atributos:

* `fireTime`: tempo do disparo
* `bulletSpeed`: velocidade da bala
* `direction`: direção lateral do movimento esperado
* `startX`, `startY`: ponto de origem do tiro
* `srcBearing`: ângulo absoluto do disparo (radianos)

### Função:

Permitir que, ao passar o tempo, seja possível comparar a posição real do inimigo com a esperada e atualizar os bins de mira.

## 6. Lógica de Mira: GuessFactor Targeting

### Conceito:

* Assume que o inimigo tem um padrão de movimento relativo ao tiro
* Mapeia isso em um array de 31 bins (de -1 a +1 normalizado)
* Mira no bin mais frequente historicamente

### Processo:

1. Registra o disparo (wave)
2. Quando a onda alcança a posição estimada do inimigo, calcula o erro
3. Traduz o erro em um bin e incrementa no histograma
4. Nas próximas tentativas, mira no bin com mais ocorrências

## 7. Movimento: Anti-Gravity

### Objetivo:

Evitar ser um alvo previsível e manter distância de segurança

### Como funciona:

1. Cada inimigo exerce uma "força de repulsão"
2. O centro da arena exerce uma "força de atração"
3. As forças são vetorialmente somadas para definir o ângulo de movimento

## 8. Radar: Lock-on Radar

Mantém o radar permanentemente ajustado para cobrir o inimigo, girando um pouco mais que o ângulo calculado:

```java
setTurnRadarRight(radarOffset * 1.2);
```

Garante que, mesmo se o inimigo se mover levemente, ele continue sendo rastreado.

## 9. Eventos de Combate

| Evento          | Reação                          |
| --------------- | ------------------------------- |
| `onHitByBullet` | Desvio lateral (90º do disparo) |
| `onHitWall`     | Recua e vira 90º                |
| `onRobotDeath`  | Remove inimigo do mapa          |

## 11. Conclusão

O `Tronos` é um robô robusto, modular e estratégico, que utiliza técnicas modernas e eficazes para combate. Seu design combina inteligência preditiva com evasão ativa, oferecendo excelente desempenho em duelos 1x1 e potencial para evoluir em batalhas múltiplas.
