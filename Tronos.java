import robocode.*;
import java.awt.*;
import java.util.*;
import java.awt.geom.Point2D;

public class Tronos extends AdvancedRobot {
    private Map<String, EnemyBot> enemies = new HashMap<>();
    private static final double MAX_FIRE_POWER = 3.0;
    private static final double MIN_FIRE_POWER = 0.1;

    @Override
    public void run() {
        setColors(Color.WHITE, Color.WHITE, Color.WHITE); // Corpo, canhão, radar
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Gira radar infinitamente até encontrar inimigo
        setTurnRadarRight(360);
        execute();

        while (true) {
            // Atualiza ondas (GuessFactor) e remove ondas resolvidas
            long currentTime = getTime();
            for (String name : new ArrayList<>(enemies.keySet())) {
                EnemyBot eb = enemies.get(name);
                eb.updateWaves(currentTime, this);
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        String enemyName = e.getName();
        double distance = e.getDistance();

        // Atualiza/Cria EnemyBot
        EnemyBot enemy = enemies.getOrDefault(enemyName, new EnemyBot());
        enemy.update(e, this);
        enemies.put(enemyName, enemy);

        // Remova bots “fantasmas” (se morreram sem disparar onRobotDeath)
        // (opcional: verificar se tempo desde lastScan > threshold e remover)

        // TRAVANDO O RADAR no inimigo
        double futureX = enemy.lastX;
        double futureY = enemy.lastY;
        double absBearing = absoluteBearing(getX(), getY(), futureX, futureY);
        double radarOffset = normalizeBearing(absBearing - getRadarHeading());
        setTurnRadarRight(radarOffset * 1.2);

        // SELEÇÃO DE ALVO: escolhe o inimigo mais próximo
        EnemyBot primary = null;
        double bestDist = Double.MAX_VALUE;
        for (EnemyBot eb : enemies.values()) {
            if (eb.lastDistance < bestDist) {
                bestDist = eb.lastDistance;
                primary = eb;
            }
        }

        if (primary != null && primary.name.equals(enemyName)) {
            // -------- TARGETING (GuessFactor) --------
            // Calcula potência de tiro dinâmica:
            double firePower;
            double energyDrop = primary.lastEnergy - e.getEnergy();
            if (energyDrop > 0 && energyDrop <= 3) {
                // inimigo disparou recentemente => evadir primeiro, sem atirar
                firePower = 0.0;
            } else {
                if (distance > 400) firePower = 1;
                else if (distance > 200) firePower = 2;
                else firePower = 3;
            }

            if (firePower > 0 && getGunHeat() == 0) {
                double bulletSpeed = 20 - 3 * firePower;
                double offsetGF = primary.getBestGuessFactor() * primary.maxEscapeAngle(bulletSpeed) * primary.direction();
                double gunTurnDeg = normalizeBearing(absBearing - getGunHeading() + Math.toDegrees(offsetGF));
                setTurnGunRight(gunTurnDeg);

                if (Math.abs(getGunTurnRemaining()) < 10) {
                    setFire(firePower);
                    // registra a onda de tiro
                    primary.registerWave(new Wave(getTime(), bulletSpeed, Math.signum(gunTurnDeg), getX(), getY(), primary));
                }
            }

            primary.lastEnergy = e.getEnergy();
        }

        // -------- MOVIMENTAÇÃO (ANTI-GRAVIDADE) --------
        double moveAngle = calcMoveAngle();
        double turnAngle = normalizeBearing(moveAngle - getHeading());
        setTurnRight(turnAngle);
        setAhead(100);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // Manobra evasiva rápida
        setTurnRight(normalizeBearing(90 - e.getBearing()));
        setAhead(150);
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        setBack(100);
        setTurnRight(90);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        enemies.remove(e.getName());
    }

    // =================== UTILITÁRIOS ===================

    private double absoluteBearing(double x1, double y1, double x2, double y2) {
        return Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
    }

    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private double calcMoveAngle() {
        double x = getX(), y = getY();
        double forceX = 0, forceY = 0;

        // Forças dos inimigos
        for (EnemyBot e : enemies.values()) {
            if (e == null) continue;
            double dx = x - e.lastX;
            double dy = y - e.lastY;
            double dist = Point2D.distance(x, y, e.lastX, e.lastY);
            if (dist == 0) continue;
            double magnitude = 2000 / (dist * dist);
            forceX += (dx / dist) * magnitude;
            forceY += (dy / dist) * magnitude;
        }

        // Forças das paredes (atração ao centro)
        double centerX = getBattleFieldWidth() / 2;
        double centerY = getBattleFieldHeight() / 2;
        double distCenter = Point2D.distance(x, y, centerX, centerY);
        double magCenter = 5000 / (distCenter * distCenter);
        double dxC = centerX - x;
        double dyC = centerY - y;
        forceX += (dxC / distCenter) * magCenter;
        forceY += (dyC / distCenter) * magCenter;

        return Math.toDegrees(Math.atan2(forceX, forceY));
    }

    // =================== CLASSES AUXILIARES ===================
    class EnemyBot {
        public static final int BINS = 31;
        public String name;
        public double lastDistance, lastBearing, lastHeading, lastVelocity;
        public double lastX, lastY;
        public double lastEnergy = 100;
        public long lastScanTime;

        // GuessFactor data
        public int[] guessFactors = new int[BINS];
        public ArrayList<Wave> waves = new ArrayList<>();

        // Atualiza dados do inimigo
        public void update(ScannedRobotEvent e, AdvancedRobot robot) {
            this.name = e.getName();
            this.lastDistance = e.getDistance();
            this.lastBearing = e.getBearing();
            this.lastHeading = e.getHeading();
            this.lastVelocity = e.getVelocity();
            this.lastScanTime = robot.getTime();

            double absBearing = Math.toRadians(
                absoluteBearing(robot.getX(), robot.getY(),
                    robot.getX() + Math.sin(Math.toRadians(robot.getHeading() + e.getBearing())) * e.getDistance(),
                    robot.getY() + Math.cos(Math.toRadians(robot.getHeading() + e.getBearing())) * e.getDistance()
                )
            );
            this.lastX = robot.getX() + Math.sin(absBearing) * e.getDistance();
            this.lastY = robot.getY() + Math.cos(absBearing) * e.getDistance();
        }

        // Registra uma nova onda de tiro para esse inimigo
        public void registerWave(Wave w) {
            waves.add(w);
        }

        // Atualiza ondas e bins quando elas "acertam/missam"
        public void updateWaves(long currentTime, AdvancedRobot robot) {
            Iterator<Wave> it = waves.iterator();
            while (it.hasNext()) {
                Wave w = it.next();
                // Se já passou da distância em que a onda alcançaria o inimigo
                double traveled = (currentTime - w.fireTime) * w.bulletSpeed;
                double dist = Point2D.distance(w.startX, w.startY, lastX, lastY);
                if (Math.abs(traveled - dist) < w.bulletSpeed) {
                    // Calcula ângulo real do inimigo quando a onda chegou
                    double actualBearing = Math.atan2(lastX - w.startX, lastY - w.startY);
                    double bearingDiff = normalizeBearingRad(actualBearing - w.srcBearing);
                    double maxEscape = maxEscapeAngle(w.bulletSpeed);
                    double guess = bearingDiff / maxEscape * w.direction;

                    // Converte “guess” (-1..1) em índice de bin
                    int index = (int) Math.round(((guess + 1) / 2) * (BINS - 1));
                    index = Math.max(0, Math.min(BINS - 1, index));
                    guessFactors[index]++; // incrementa o bin

                    it.remove();
                }
            }
        }

        // Retorna o fator de guess ótimo para mirar (entre -maxEscapeAngle e +maxEscapeAngle)
        public double getBestGuessFactor() {
            int bestIndex = (BINS - 1) / 2;
            int max = -1;
            for (int i = 0; i < BINS; i++) {
                if (guessFactors[i] > max) {
                    max = guessFactors[i];
                    bestIndex = i;
                }
            }
            return ((double)(bestIndex - (BINS - 1) / 2)) / ((BINS - 1) / 2);
        }

        // Calcula o “escape angle” máximo baseado na velocidade do inimigo
        public double maxEscapeAngle(double bulletSpeed) {
            return Math.asin(8.0 / bulletSpeed); // no Robocode, velocidade máxima é 8
        }

        // Determina direção (+1 ou -1) com que o inimigo se move lateralmente em relação a mim
        public double direction() {
            // Se a velocidade for zero, assume +1
            if (lastVelocity == 0) return 1;
            double absBearing = Math.toRadians(absoluteBearing(getX(), getY(), lastX, lastY));
            double enemyHeadingRad = Math.toRadians(lastHeading);
            double angle = Math.sin(enemyHeadingRad - absBearing) * lastVelocity;
            return angle < 0 ? -1 : 1;
        }

        // Normaliza ângulo em radianos para [-PI,PI]
        private double normalizeBearingRad(double ang) {
            while (ang > Math.PI) ang -= 2 * Math.PI;
            while (ang < -Math.PI) ang += 2 * Math.PI;
            return ang;
        }
    }

    class Wave {
        public long fireTime;
        public double bulletSpeed;
        public double direction;   // +1 ou -1
        public double startX, startY;
        public double srcBearing;  // ângulo absoluto em radianos

        public Wave(long fireTime, double bulletSpeed, double direction, double startX, double startY, EnemyBot target) {
            this.fireTime = fireTime;
            this.bulletSpeed = bulletSpeed;
            this.direction = direction;
            this.startX = startX;
            this.startY = startY;
            // Guarda o ângulo absoluto inicial para comparar depois
            this.srcBearing = Math.toRadians(absoluteBearing(startX, startY, target.lastX, target.lastY));
        }
    }
}
