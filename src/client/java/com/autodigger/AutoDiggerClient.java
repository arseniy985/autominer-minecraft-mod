package com.autodigger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3i;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class AutoDiggerClient implements ClientModInitializer {
    
    private static boolean isDigging = false;
    private static int corridorDistance = 3; // расстояние между туннелями
    private static int corridorLength = 32; // длина коридора
    private static int humanizationLevel = 5; // уровень человечности (0-10)
    private static int detectionRadius = 300; // радиус обнаружения игроков (в блоках)
    
    // Система безопасности
    private static Set<String> whitelistedPlayers = new HashSet<>();
    private static boolean safetyEnabled = true;
    private static int playerCheckCounter = 0;
    private static final int PLAYER_CHECK_INTERVAL = 20; // проверка каждую секунду
    
    // Автоматические команды
    private static List<String> autoCommands = new ArrayList<>();
    private static int commandCounter = 0;
    private static int nextCommandDelay = 200; // 10 секунд начальная задержка
    private static boolean waitingForNearResponse = false;
    private static long nearCommandTime = 0;
    
    // Система питания
    private static int hungerCheckCounter = 0;
    private static final int HUNGER_CHECK_INTERVAL = 100; // проверка каждые 5 секунд
    
    // Ключевые слова для /near
    private static Set<String> safeKeywords = new HashSet<>();
    private static Set<String> dangerKeywords = new HashSet<>();
    
    // Система умного копания в мезе
    private static boolean mesaMode = true; // режим копания только в мезе
    private static boolean goldDetection = true; // детекция золота
    private static int goldScanRadius = 3; // радиус сканирования золота
    private static List<BlockPos> detectedGoldOres = new ArrayList<>();
    private static boolean miningGold = false;
    private static int currentGoldIndex = 0;
    
    // Система адаптивного направления
    private static List<Direction> preferredDirections = new ArrayList<>();
    private static int directionCheckCounter = 0;
    private static final int DIRECTION_CHECK_INTERVAL = 60; // проверка каждые 3 секунды
    
    // ============ СИСТЕМА ПОЛНОГО КОПАНИЯ ЖИЛ РУДЫ (VEIN MINING) ============
    private static boolean veinMiningEnabled = true; // включено по умолчанию
    private static int veinScanRadius = 8; // радиус поиска начала жилы
    private static int maxVeinSize = 200; // максимальный размер жилы для безопасности
    private static boolean miningVein = false; // активно ли копание жилы
    private static List<BlockPos> veinBlocks = new ArrayList<>(); // все блоки текущей жилы
    private static Set<BlockPos> scannedBlocks = new HashSet<>(); // просканированные блоки
    private static int currentVeinIndex = 0; // текущий индекс в жиле
    private static Block currentVeinOreType = null; // тип руды текущей жилы
    private static BlockPos lastVeinStartPos = null; // последняя позиция начала поиска жилы
    
    // Типы руд для поиска жил
    private static final Set<Block> VEIN_ORES = Set.of(
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS
    );
    
    // Состояние для копания
    private static int currentCorridorBlocks = 0;
    private static int currentCorridor = 0;
    private static boolean movingToNextCorridor = false;
    private static int moveRightBlocks = 0;
    private static BlockPos startPos = null;
    private static Direction facingDirection = null;
    private static BlockPos currentTargetPos = null;
    
    // Счетчик тиков для контроля скорости с случайностью
    private static int tickCounter = 0;
    private static int currentDigDelay = 0;
    private static final int BASE_DIG_DELAY = 12; // базовая задержка между блоками (более медленно)
    private static final int DIG_DELAY_VARIANCE = 8; // вариация задержки (больше случайности)
    
    // Система плавных поворотов
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static float currentYaw = 0;
    private static float currentPitch = 0;
    private static final float ROTATION_SPEED = 2.5f; // скорость поворота (более медленно для естественности)
    
    // Система имитации усталости и микропауз
    private static int fatigueCounter = 0;
    private static boolean isFatigued = false;
    private static int microPauseCounter = 0;
    
    // Система естественных движений
    private static Vec3d targetPosition = null;
    private static final Random random = new Random();
    private static int pauseCounter = 0;
    private static int nextPauseDuration = 0;
    
    // Контроль движения
    private static int moveDelay = 0;
    private static final int BASE_MOVE_DELAY = 15;
    private static final int MOVE_DELAY_VARIANCE = 8;
    
    // Система имитации осмотра окрестностей
    private static int lookAroundCounter = 0;
    private static boolean isLookingAround = false;
    private static float lookAroundYaw = 0;
    private static int lookAroundDuration = 0;
    
    @Override
    public void onInitializeClient() {
        AutoDiggerMod.LOGGER.info("Auto Digger клиент инициализируется!");
        
        // Обработка сообщений в чате
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            MinecraftClient client = MinecraftClient.getInstance();
            
            // Анализ ответа на команду /near
            if (waitingForNearResponse && System.currentTimeMillis() - nearCommandTime < 10000) {
                String messageText = text.toLowerCase();
                
                // Проверяем на опасные слова
                for (String dangerWord : dangerKeywords) {
                    if (messageText.contains(dangerWord.toLowerCase())) {
                        emergencyDisconnect(client, "NEAR команда обнаружила игроков: " + dangerWord);
                        return;
                    }
                }
                
                // Проверяем на безопасные слова
                boolean isSafe = false;
                for (String safeWord : safeKeywords) {
                    if (messageText.contains(safeWord.toLowerCase())) {
                        isSafe = true;
                        break;
                    }
                }
                
                if (isSafe) {
                    AutoDiggerMod.LOGGER.info("NEAR проверка: безопасно - " + messageText);
                } else {
                    AutoDiggerMod.LOGGER.warn("NEAR проверка: неопределенный ответ - " + messageText);
                }
                
                waitingForNearResponse = false;
            }
            
            // Проверяем, что это сообщение от текущего игрока (команда)
            if (client.player != null && text.contains(client.player.getName().getString())) {
                String[] parts = text.split(": ");
                if (parts.length > 1) {
                    String command = parts[1].trim();
                    
                    if (command.equals(".startdig")) {
                        startDigging(client);
                        return;
                    }
                    
                    if (command.startsWith(".coridor ")) {
                        try {
                            int distance = Integer.parseInt(command.substring(9).trim());
                            if (distance > 0 && distance <= 20) {
                                corridorDistance = distance;
                                client.player.sendMessage(Text.literal("§aРасстояние между коридорами установлено: " + distance), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cРасстояние должно быть от 1 до 20!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.startsWith(".lengthcoridor ")) {
                        try {
                            int length = Integer.parseInt(command.substring(15).trim());
                            if (length > 0 && length <= 200) {
                                corridorLength = length;
                                client.player.sendMessage(Text.literal("§aДлина коридора установлена: " + length), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cДлина должна быть от 1 до 200!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.equals(".stopdig")) {
                        stopDigging(client);
                        return;
                    }
                    
                    if (command.equals(".status")) {
                        showStatus(client);
                        return;
                    }
                    
                    if (command.startsWith(".human ")) {
                        try {
                            int level = Integer.parseInt(command.substring(7).trim());
                            if (level >= 0 && level <= 10) {
                                humanizationLevel = level;
                                String description = getHumanizationDescription(level);
                                client.player.sendMessage(Text.literal("§aУровень человечности установлен: " + level + "/10"), false);
                                client.player.sendMessage(Text.literal("§b" + description), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cУровень должен быть от 0 до 10!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.startsWith(".radius ")) {
                        try {
                            int radius = Integer.parseInt(command.substring(8).trim());
                            if (radius >= 5 && radius <= 500) {
                                detectionRadius = radius;
                                client.player.sendMessage(Text.literal("§aРадиус обнаружения установлен: " + radius + " блоков"), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cРадиус должен быть от 5 до 500!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.equals(".safety on")) {
                        safetyEnabled = true;
                        client.player.sendMessage(Text.literal("§aСистема безопасности включена"), false);
                        return;
                    }
                    
                    if (command.equals(".safety off")) {
                        safetyEnabled = false;
                        client.player.sendMessage(Text.literal("§cСистема безопасности отключена"), false);
                        return;
                    }
                    
                    if (command.equals(".reload")) {
                        loadAllConfigs();
                        client.player.sendMessage(Text.literal("§aВсе конфиги перезагружены:"), false);
                        client.player.sendMessage(Text.literal("§b- Whitelist: " + whitelistedPlayers.size() + " игроков"), false);
                        client.player.sendMessage(Text.literal("§b- Команды: " + autoCommands.size() + " шт."), false);
                        client.player.sendMessage(Text.literal("§b- Безопасные слова: " + safeKeywords.size() + " шт."), false);
                        client.player.sendMessage(Text.literal("§b- Опасные слова: " + dangerKeywords.size() + " шт."), false);
                        return;
                    }
                    
                    if (command.equals(".testfood")) {
                        manageFood(client);
                        client.player.sendMessage(Text.literal("§aПроверка системы питания выполнена"), false);
                        return;
                    }
                    
                    if (command.equals(".mesa on")) {
                        mesaMode = true;
                        client.player.sendMessage(Text.literal("§aРежим копания в мезе включен"), false);
                        return;
                    }
                    
                    if (command.equals(".mesa off")) {
                        mesaMode = false;
                        client.player.sendMessage(Text.literal("§cРежим копания в мезе отключен"), false);
                        return;
                    }
                    
                    if (command.equals(".gold on")) {
                        goldDetection = true;
                        client.player.sendMessage(Text.literal("§aДетекция золота включена"), false);
                        return;
                    }
                    
                    if (command.equals(".gold off")) {
                        goldDetection = false;
                        client.player.sendMessage(Text.literal("§cДетекция золота отключена"), false);
                        return;
                    }
                    
                    if (command.startsWith(".goldscan ")) {
                        try {
                            int radius = Integer.parseInt(command.substring(10).trim());
                            if (radius >= 1 && radius <= 8) {
                                goldScanRadius = radius;
                                client.player.sendMessage(Text.literal("§aРадиус сканирования золота установлен: " + radius), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cРадиус должен быть от 1 до 8!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.equals(".checkbiome")) {
                        if (client.player != null && client.world != null) {
                            checkCurrentBiome(client);
                        }
                        return;
                    }
                    
                    // ============ КОМАНДЫ ДЛЯ VEIN MINING ============
                    
                    if (command.equals(".vein on")) {
                        veinMiningEnabled = true;
                        client.player.sendMessage(Text.literal("§a✅ Полное копание жил руды включено!"), false);
                        client.player.sendMessage(Text.literal("§eBудет искать и копать всю жилу целиком"), false);
                        return;
                    }
                    
                    if (command.equals(".vein off")) {
                        veinMiningEnabled = false;
                        if (miningVein) {
                            finishVeinMining(client);
                        }
                        client.player.sendMessage(Text.literal("§c❌ Полное копание жил руды отключено!"), false);
                        return;
                    }
                    
                    if (command.startsWith(".veinradius ")) {
                        try {
                            int radius = Integer.parseInt(command.substring(12).trim());
                            if (radius >= 2 && radius <= 16) {
                                veinScanRadius = radius;
                                client.player.sendMessage(Text.literal("§aРадиус поиска жил установлен: " + radius), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cРадиус должен быть от 2 до 16!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.startsWith(".maxvein ")) {
                        try {
                            int maxSize = Integer.parseInt(command.substring(9).trim());
                            if (maxSize >= 50 && maxSize <= 1000) {
                                maxVeinSize = maxSize;
                                client.player.sendMessage(Text.literal("§aМаксимальный размер жилы установлен: " + maxSize), false);
                            } else {
                                client.player.sendMessage(Text.literal("§cРазмер должен быть от 50 до 1000!"), false);
                            }
                        } catch (NumberFormatException e) {
                            client.player.sendMessage(Text.literal("§cНеверный формат числа!"), false);
                        }
                        return;
                    }
                    
                    if (command.equals(".veininfo")) {
                        showVeinInfo(client);
                        return;
                    }
                    
                    if (command.equals(".help")) {
                        showHelp(client);
                        return;
                    }
                }
            }
        });
        
        // Основной игровой цикл
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isDigging && client.player != null && client.world != null) {
                
                // КРИТИЧНО: Проверка безопасности перед всеми действиями
                playerCheckCounter++;
                if (playerCheckCounter >= PLAYER_CHECK_INTERVAL) {
                    playerCheckCounter = 0;
                    if (safetyEnabled && checkForDangerousPlayers(client)) {
                        return; // выходим из цикла, копание уже остановлено
                    }
                }
                
                // Автоматические команды
                commandCounter++;
                if (commandCounter >= nextCommandDelay) {
                    executeAutoCommand(client);
                    commandCounter = 0;
                    // Следующая команда через 10-20 секунд (200-400 тиков)
                    nextCommandDelay = 200 + random.nextInt(200);
                }
                
                // Система питания
                hungerCheckCounter++;
                if (hungerCheckCounter >= HUNGER_CHECK_INTERVAL) {
                    hungerCheckCounter = 0;
                    manageFood(client);
                }
                
                // Проверка направления и биома для умного копания
                directionCheckCounter++;
                if (directionCheckCounter >= DIRECTION_CHECK_INTERVAL) {
                    directionCheckCounter = 0;
                    if (mesaMode) {
                        updatePreferredDirections(client);
                    }
                }
                
                // Детекция золота
                if (goldDetection && tickCounter % 40 == 0) { // каждые 2 секунды
                    scanForGold(client);
                }
                
                // Система полного копания жил руды
                if (veinMiningEnabled && !miningVein && tickCounter % 60 == 0) { // каждые 3 секунды
                    scanForVeins(client);
                }
                
                // Обновляем плавные повороты
                updateSmoothRotation(client.player);
                
                // Система случайных пауз (имитация размышлений игрока)
                if (pauseCounter > 0) {
                    pauseCounter--;
                    return;
                }
                
                // Система пауз зависит от уровня человечности
                if (humanizationLevel > 0) {
                    // Обычные паузы
                    int pauseChance = Math.max(1000 - (humanizationLevel * 70), 100); // от 1000 до 300
                    if (random.nextInt(pauseChance) == 0) {
                        int pauseDuration = (10 + random.nextInt(30)) * humanizationLevel / 5; // больше пауз при высоком уровне
                        pauseCounter = Math.max(pauseDuration, 5);
                        return;
                    }
                    
                    // Система усталости
                    if (humanizationLevel >= 3) {
                        fatigueCounter++;
                        int fatigueThreshold = 1800 - (humanizationLevel * 60); // от 1800 до 1200
                        if (fatigueCounter > fatigueThreshold) {
                            int fatigueChance = Math.max(200 - (humanizationLevel * 15), 50); // от 200 до 50
                            if (random.nextInt(fatigueChance) == 0) {
                                pauseCounter = (30 + random.nextInt(90)) * humanizationLevel / 5; // 3-18 секунд
                                isFatigued = true;
                                if (client.player != null && humanizationLevel >= 5) {
                                    client.player.sendMessage(Text.literal("§e*Делаю небольшую передышку...*"), false);
                                }
                                fatigueCounter = 0;
                                return;
                            }
                        }
                    }
                    
                    // Микропаузы
                    if (humanizationLevel >= 2) {
                        microPauseCounter++;
                        int microInterval = Math.max(20 - humanizationLevel, 5);
                        if (microPauseCounter > microInterval + random.nextInt(10)) {
                            int microChance = Math.max(80 - (humanizationLevel * 5), 20); // от 80 до 30
                            if (random.nextInt(microChance) == 0) {
                                pauseCounter = 2 + random.nextInt(humanizationLevel); // 0.1-0.5 секунд
                                microPauseCounter = 0;
                                return;
                            }
                            microPauseCounter = 0;
                        }
                    }
                }
                
                // Система осмотра окрестностей зависит от уровня человечности
                if (humanizationLevel >= 4) {
                    int lookInterval = Math.max(600 - (humanizationLevel * 40), 100); // от 600 до 200 тиков
                    if (lookAroundCounter++ > lookInterval + random.nextInt(200)) {
                        startLookingAround();
                        lookAroundCounter = 0;
                    }
                }
                
                if (isLookingAround) {
                    performLookAround(client.player);
                    return;
                }
                
                tickCounter++;
                if (tickCounter >= currentDigDelay) {
                    tickCounter = 0;
                    // Генерируем новую случайную задержку с учетом уровня человечности
                    if (humanizationLevel == 0) {
                        currentDigDelay = 3; // минимальная задержка при уровне 0
                    } else {
                        int baseDelay = Math.max(15 - humanizationLevel, 3); // от 15 до 5 тиков
                        int variance = Math.max(humanizationLevel * 2, 1); // от 2 до 20 тиков
                        currentDigDelay = baseDelay + random.nextInt(variance);
                    }
                    performDigging(client);
                }
                
                // Проверка прочности кирки каждые 20 тиков
                if (client.player.age % 20 == 0) {
                    checkPickaxeDurability(client);
                }
            }
        });
    }
    
    private static void startDigging(MinecraftClient client) {
        if (client.player == null) return;
        
        // Проверка наличия кирки
        if (!hasPickaxe(client.player)) {
            client.player.sendMessage(Text.literal("§cНужна кирка для копания!"), false);
            return;
        }
        
        isDigging = true;
        currentCorridorBlocks = 0;
        currentCorridor = 0;
        movingToNextCorridor = false;
        moveRightBlocks = 0;
        startPos = client.player.getBlockPos();
        facingDirection = client.player.getHorizontalFacing();
        currentTargetPos = startPos.offset(facingDirection);
        moveDelay = 0;
        pauseCounter = 0;
        lookAroundCounter = 0;
        isLookingAround = false;
        
        // Инициализируем систему поворотов
        currentYaw = client.player.getYaw();
        currentPitch = client.player.getPitch();
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        
        // Начальная случайная задержка
        currentDigDelay = BASE_DIG_DELAY + random.nextInt(DIG_DELAY_VARIANCE);
        
        // Инициализация всех систем
        loadAllConfigs();
        playerCheckCounter = 0;
        commandCounter = 0;
        hungerCheckCounter = 0;
        
        client.player.sendMessage(Text.literal("§a=== Начинаю копание коридоров ==="), false);
        client.player.sendMessage(Text.literal("§bДлина: " + corridorLength + ", расстояние: " + corridorDistance), false);
        client.player.sendMessage(Text.literal("§bНаправление: " + facingDirection.getName()), false);
        client.player.sendMessage(Text.literal("§eИспользуйте .stopdig для остановки"), false);
    }
    
    private static void stopDigging(MinecraftClient client) {
        isDigging = false;
        isLookingAround = false;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§c=== Копание остановлено ==="), false);
            client.player.sendMessage(Text.literal("§eВыкопано коридоров: " + currentCorridor), false);
        }
    }
    
    private static void showStatus(MinecraftClient client) {
        if (client.player == null) return;
        
        if (isDigging) {
            client.player.sendMessage(Text.literal("§a=== Статус копания ==="), false);
            client.player.sendMessage(Text.literal("§bАктивен: Да"), false);
            client.player.sendMessage(Text.literal("§bТекущий коридор: " + (currentCorridor + 1)), false);
            client.player.sendMessage(Text.literal("§bБлоков выкопано в коридоре: " + currentCorridorBlocks + "/" + corridorLength), false);
            client.player.sendMessage(Text.literal("§bРежим: " + (movingToNextCorridor ? "Переход к следующему коридору" : "Копание коридора")), false);
            
            // Информация о кирке
            ItemStack pickaxe = getPickaxe(client.player);
            if (pickaxe != null) {
                int durability = pickaxe.getMaxDamage() - pickaxe.getDamage();
                int maxDurability = pickaxe.getMaxDamage();
                double durabilityPercent = (double) durability / maxDurability * 100;
                client.player.sendMessage(Text.literal("§bПрочность кирки: " + durability + "/" + maxDurability + " (" + String.format("%.1f", durabilityPercent) + "%)"), false);
            }
        } else {
            client.player.sendMessage(Text.literal("§c=== Статус копания ==="), false);
            client.player.sendMessage(Text.literal("§cАктивен: Нет"), false);
            client.player.sendMessage(Text.literal("§eИспользуйте .startdig для начала"), false);
        }
        
        client.player.sendMessage(Text.literal("§bНастройки:"), false);
        client.player.sendMessage(Text.literal("§b- Длина коридора: " + corridorLength), false);
        client.player.sendMessage(Text.literal("§b- Расстояние между коридорами: " + corridorDistance), false);
        client.player.sendMessage(Text.literal("§b- Уровень человечности: " + humanizationLevel + "/10 (" + getHumanizationDescription(humanizationLevel) + ")"), false);
        client.player.sendMessage(Text.literal("§b- Система безопасности: " + (safetyEnabled ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§b- Радиус обнаружения: " + detectionRadius + " блоков"), false);
        client.player.sendMessage(Text.literal("§b- Игроков в исключениях: " + whitelistedPlayers.size()), false);
        client.player.sendMessage(Text.literal("§b- Автокоманд загружено: " + autoCommands.size()), false);
        client.player.sendMessage(Text.literal("§b- Безопасных ключевых слов: " + safeKeywords.size()), false);
        client.player.sendMessage(Text.literal("§b- Опасных ключевых слов: " + dangerKeywords.size()), false);
        if (client.player != null) {
            int foodLevel = client.player.getHungerManager().getFoodLevel();
            client.player.sendMessage(Text.literal("§b- Текущий голод: " + foodLevel + "/20"), false);
        }
        client.player.sendMessage(Text.literal("§b- Режим копания в мезе: " + (mesaMode ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§b- Детекция золота: " + (goldDetection ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§b- Радиус сканирования золота: " + goldScanRadius), false);
        client.player.sendMessage(Text.literal("§b- Обнаружено золота: " + detectedGoldOres.size() + " блоков"), false);
        if (miningGold) {
            client.player.sendMessage(Text.literal("§e- ДОБЫЧА ЗОЛОТА АКТИВНА (" + (currentGoldIndex + 1) + "/" + detectedGoldOres.size() + ")"), false);
        }
        client.player.sendMessage(Text.literal("§5- Vein Mining: " + (veinMiningEnabled ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§5- Радиус поиска жил: " + veinScanRadius + " блоков"), false);
        if (miningVein && currentVeinOreType != null) {
            client.player.sendMessage(Text.literal("§d- ДОБЫЧА ЖИЛЫ АКТИВНА: " + getOreName(currentVeinOreType)), false);
            client.player.sendMessage(Text.literal("§d- Прогресс жилы: " + currentVeinIndex + "/" + veinBlocks.size() + " блоков"), false);
        }
    }
    
    private static String getHumanizationDescription(int level) {
        return switch (level) {
            case 0 -> "Робот (только плавные повороты)";
            case 1 -> "Минимальная хуманизация";
            case 2 -> "Очень низкая хуманизация";
            case 3 -> "Низкая хуманизация";
            case 4 -> "Умеренно-низкая хуманизация";
            case 5 -> "Средняя хуманизация (по умолчанию)";
            case 6 -> "Умеренно-высокая хуманизация";
            case 7 -> "Высокая хуманизация";
            case 8 -> "Очень высокая хуманизация";
            case 9 -> "Почти как человек";
            case 10 -> "Максимальная хуманизация";
            default -> "Неизвестный уровень";
        };
    }
    
    private static void showHelp(MinecraftClient client) {
        if (client.player == null) return;
        
        client.player.sendMessage(Text.literal("§a=== Auto Digger - Команды ==="), false);
        client.player.sendMessage(Text.literal("§e.startdig §7- начать автоматическое копание"), false);
        client.player.sendMessage(Text.literal("§e.stopdig §7- остановить копание"), false);
        client.player.sendMessage(Text.literal("§e.status §7- показать текущий статус"), false);
        client.player.sendMessage(Text.literal("§e.coridor <число> §7- расстояние между коридорами (1-20)"), false);
        client.player.sendMessage(Text.literal("§e.lengthcoridor <число> §7- длина коридора (1-200)"), false);
        client.player.sendMessage(Text.literal("§e.human <0-10> §7- уровень человечности (0=робот, 10=максимум)"), false);
        client.player.sendMessage(Text.literal("§e.radius <5-500> §7- радиус обнаружения игроков (по умолчанию: 300)"), false);
        client.player.sendMessage(Text.literal("§e.safety on/off §7- включить/отключить систему безопасности"), false);
        client.player.sendMessage(Text.literal("§e.reload §7- перезагрузить все конфиги"), false);
        client.player.sendMessage(Text.literal("§e.testfood §7- проверить систему питания"), false);
        client.player.sendMessage(Text.literal("§e.mesa on/off §7- включить/отключить режим копания в мезе"), false);
        client.player.sendMessage(Text.literal("§e.gold on/off §7- включить/отключить детекцию золота"), false);
        client.player.sendMessage(Text.literal("§e.goldscan <1-8> §7- радиус сканирования золота"), false);
        client.player.sendMessage(Text.literal("§e.checkbiome §7- проверить текущий биом"), false);
        client.player.sendMessage(Text.literal("§5.vein on/off §7- включить/отключить полное копание жил"), false);
        client.player.sendMessage(Text.literal("§5.veinradius <2-16> §7- радиус поиска жил руды"), false);
        client.player.sendMessage(Text.literal("§5.maxvein <50-1000> §7- максимальный размер жилы"), false);
        client.player.sendMessage(Text.literal("§5.veininfo §7- информация о системе vein mining"), false);
        client.player.sendMessage(Text.literal("§e.help §7- показать эту справку"), false);
        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(Text.literal("§6⚠ Функции защиты от античитов:"), false);
        client.player.sendMessage(Text.literal("§7• Плавные и естественные повороты"), false);
        client.player.sendMessage(Text.literal("§7• Случайные паузы и микропаузы"), false);
        client.player.sendMessage(Text.literal("§7• Имитация усталости игрока"), false);
        client.player.sendMessage(Text.literal("§7• Неточность движений как у реального игрока"), false);
        client.player.sendMessage(Text.literal("§7• Автоматическое сохранение инструментов"), false);
        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(Text.literal("§c🛡 Система безопасности:"), false);
        client.player.sendMessage(Text.literal("§7• Автоостановка при обнаружении игроков"), false);
        client.player.sendMessage(Text.literal("§7• Настраиваемый радиус обнаружения"), false);
        client.player.sendMessage(Text.literal("§7• Файл исключений: config/whitelist.txt"), false);
        client.player.sendMessage(Text.literal("§7• Состояние: " + (safetyEnabled ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(Text.literal("§a🤖 Автоматические функции:"), false);
        client.player.sendMessage(Text.literal("§7• Команды каждые 10-20 сек (config/commands.txt)"), false);
        client.player.sendMessage(Text.literal("§7• Анализ /near команды (config/near-keywords.txt)"), false);
        client.player.sendMessage(Text.literal("§7• Автоматическое питание при голоде"), false);
        client.player.sendMessage(Text.literal("§7• ОТКЛЮЧЕНИЕ ОТ СЕРВЕРА при обнаружении игроков"), false);
        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(Text.literal("§6⛏ Умное копание в мезе:"), false);
        client.player.sendMessage(Text.literal("§7• Автоматическое изменение направления для оставания в мезе"), false);
        client.player.sendMessage(Text.literal("§7• Детекция золота в радиусе " + goldScanRadius + " блоков"), false);
        client.player.sendMessage(Text.literal("§7• Автоматическая добыча найденного золота"), false);
        client.player.sendMessage(Text.literal("§7• Состояние меза-режима: " + (mesaMode ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§7• Состояние детекции золота: " + (goldDetection ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(Text.literal("§5💎 Полное копание жил руды (Vein Mining):"), false);
        client.player.sendMessage(Text.literal("§7• Автоматический поиск всех типов руд"), false);
        client.player.sendMessage(Text.literal("§7• Копание ВСЕЙ жилы целиком (без ограничений радиуса)"), false);
        client.player.sendMessage(Text.literal("§7• Поддержка всех руд: железо, золото, алмазы, древние обломки..."), false);
        client.player.sendMessage(Text.literal("§7• Умное определение связанных блоков руды"), false);
        client.player.sendMessage(Text.literal("§7• Состояние vein mining: " + (veinMiningEnabled ? "§aВКЛ" : "§cВЫКЛ")), false);
    }
    
    private static void performDigging(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) return;
        
        // Приоритет 1: Добыча обнаруженной жилы руды
        if (miningVein && !veinBlocks.isEmpty()) {
            mineVeinBlocks(client);
            return;
        }
        
        // Приоритет 2: Добыча золота (старая система)
        if (miningGold && !detectedGoldOres.isEmpty()) {
            mineDetectedGold(client);
            return;
        }
        
        // Проверка биома для умного копания
        if (mesaMode && !isInMesaBiome(client, player.getBlockPos())) {
            findBetterDirection(client);
        }
        
        if (movingToNextCorridor) {
            // Двигаемся вправо к следующему коридору
            if (moveRightBlocks < corridorDistance) {
                Direction rightDirection = getRightDirection(facingDirection);
                currentTargetPos = player.getBlockPos().offset(rightDirection);
                
                // Копаем блоки на пути если нужно
                digBlocksAtPosition(client, currentTargetPos);
                
                moveRightBlocks++;
                if (moveRightBlocks >= corridorDistance) {
                    movingToNextCorridor = false;
                    currentCorridorBlocks = 0;
                    currentCorridor++;
                    client.player.sendMessage(Text.literal("§aНачинаю коридор #" + (currentCorridor + 1)), false);
                }
            }
        } else {
            // Копаем коридор вперед
            if (currentCorridorBlocks < corridorLength) {
                currentTargetPos = startPos.offset(facingDirection, currentCorridorBlocks + 1).offset(getRightDirection(facingDirection), currentCorridor * corridorDistance);
                
                // Копаем блоки на текущей позиции
                digBlocksAtPosition(client, currentTargetPos);
                
                currentCorridorBlocks++;
                
                // Показываем прогресс каждые 10 блоков
                if (currentCorridorBlocks % 10 == 0) {
                    client.player.sendMessage(Text.literal("§eПрогресс коридора " + (currentCorridor + 1) + ": " + currentCorridorBlocks + "/" + corridorLength), false);
                }
            } else {
                // Коридор закончен, переходим к движению вправо
                movingToNextCorridor = true;
                moveRightBlocks = 0;
                client.player.sendMessage(Text.literal("§aКоридор #" + (currentCorridor + 1) + " завершен! Переход к следующему..."), false);
            }
        }
    }
    
    private static void digBlocksAtPosition(MinecraftClient client, BlockPos centerPos) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) return;
        
        // Сначала устанавливаем целевое направление взгляда
        Vec3d blockCenter = Vec3d.ofCenter(centerPos);
        setTargetLook(blockCenter, player.getPos());
        
        // Копаем блоки на уровне игрока и выше головы (высота туннеля 2 блока)
        for (int y = 0; y <= 1; y++) {
            BlockPos targetPos = centerPos.up(y);
            
            Block block = world.getBlockState(targetPos).getBlock();
            
            // Не копаем воздух, коренную породу и жидкости
            if (block != Blocks.AIR && 
                block != Blocks.BEDROCK && 
                block != Blocks.WATER && 
                block != Blocks.LAVA &&
                block != Blocks.VOID_AIR &&
                block != Blocks.CAVE_AIR) {
                
                // Симулируем копание блока с учетом уровня человечности
                if (client.interactionManager != null) {
                    // Промахи только при уровне человечности > 2
                    if (humanizationLevel >= 3) {
                        int missChance = Math.max(50 - humanizationLevel * 3, 10);
                        if (random.nextInt(missChance) == 0) {
                            continue; // пропускаем удар
                        }
                    }
                    
                    // Направление атаки зависит от уровня человечности
                    Direction attackDirection;
                    if (humanizationLevel == 0) {
                        // Робот всегда атакует оптимально
                        attackDirection = Direction.UP;
                    } else if (humanizationLevel <= 3) {
                        // Низкий уровень - в основном логичные удары
                        attackDirection = random.nextBoolean() ? Direction.UP : Direction.DOWN;
                    } else {
                        // Высокий уровень - больше вариативности
                        if (random.nextInt(4) == 0) {
                            attackDirection = Direction.values()[random.nextInt(Direction.values().length)];
                        } else {
                            attackDirection = random.nextBoolean() ? Direction.UP : Direction.DOWN;
                        }
                    }
                    
                    // Количество ударов зависит от уровня человечности
                    int hitCount;
                    if (humanizationLevel == 0) {
                        hitCount = 1; // робот всегда один удар
                    } else {
                        hitCount = 1 + random.nextInt(Math.min(humanizationLevel / 3 + 1, 3));
                    }
                    
                    for (int hit = 0; hit < hitCount; hit++) {
                        client.interactionManager.attackBlock(targetPos, attackDirection);
                        
                        // Задержка между ударами только при уровне > 0
                        if (humanizationLevel > 0 && hit > 0 && random.nextInt(3) == 0) {
                            try {
                                int delay = 5 + random.nextInt(humanizationLevel * 5); // до 50ms
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Плавно двигаем игрока к цели с небольшими отклонениями
        movePlayerToTarget(player, centerPos);
        
        // Детекция золота в окрестностях после копания
        if (goldDetection) {
            scanForGoldAroundPosition(client, centerPos);
        }
    }
    
    private static void movePlayerToTarget(ClientPlayerEntity player, BlockPos targetPos) {
        Vec3d playerPos = player.getPos();
        Vec3d targetCenter = Vec3d.ofBottomCenter(targetPos);
        
        // Добавляем случайное отклонение в зависимости от уровня человечности
        if (humanizationLevel > 0) {
            double offsetMultiplier = humanizationLevel * 0.05; // от 0.05 до 0.5
            double randomOffsetX = (random.nextDouble() - 0.5) * offsetMultiplier;
            double randomOffsetZ = (random.nextDouble() - 0.5) * offsetMultiplier;
            targetCenter = targetCenter.add(randomOffsetX, 0, randomOffsetZ);
        }
        
        Vec3d direction = targetCenter.subtract(playerPos);
        
        // Движемся только если расстояние больше определенного порога
        double minDistance = humanizationLevel == 0 ? 0.1 : 0.2;
        if (direction.length() > minDistance) {
            // Базовая скорость зависит от уровня человечности
            double baseSpeed;
            if (humanizationLevel == 0) {
                baseSpeed = 0.12; // быстрое движение для робота
            } else {
                baseSpeed = isFatigued ? 0.03 : (0.06 + humanizationLevel * 0.005); // от 0.06 до 0.11
            }
            
            double speed = baseSpeed;
            
            // Добавляем вариативность только если уровень > 0
            if (humanizationLevel > 0) {
                speed += random.nextDouble() * (humanizationLevel * 0.01); // больше вариации при высоком уровне
                
                // Имитация неровного движения
                if (humanizationLevel >= 3) {
                    int slowChance = Math.max(50 - humanizationLevel * 3, 15);
                    int fastChance = Math.max(60 - humanizationLevel * 4, 20);
                    
                    if (random.nextInt(slowChance) == 0) {
                        speed *= 0.3; // иногда замедляемся
                    } else if (random.nextInt(fastChance) == 0) {
                        speed *= 1.5; // иногда ускоряемся
                    }
                }
            }
            
            Vec3d movement = direction.normalize().multiply(speed);
            
            // Добавляем боковые отклонения только при высоком уровне человечности
            if (humanizationLevel >= 5) {
                double sideOffset = (random.nextDouble() - 0.5) * (humanizationLevel * 0.003);
                Vec3d sideDirection = new Vec3d(-direction.z, 0, direction.x).normalize();
                movement = movement.add(sideDirection.multiply(sideOffset));
            }
            
            // Применяем движение
            player.setVelocity(movement.x, player.getVelocity().y, movement.z);
        }
        
        // Сбрасываем усталость после движения (только если включена хуманизация)
        if (humanizationLevel >= 3 && isFatigued && random.nextInt(100) == 0) {
            isFatigued = false;
        }
    }
    
    private static void setTargetLook(Vec3d target, Vec3d playerPos) {
        Vec3d direction = target.subtract(playerPos);
        
        // Вычисляем углы
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        
        targetYaw = (float) (Math.atan2(-direction.x, direction.z) * 180.0 / Math.PI);
        targetPitch = (float) (Math.atan2(-direction.y, horizontalDistance) * 180.0 / Math.PI);
        
        // Добавляем неточность в зависимости от уровня человечности
        if (humanizationLevel > 0) {
            // Базовая неточность зависит от уровня
            float baseYawVariance = humanizationLevel * 1.0f; // от 1 до 10 градусов
            float basePitchVariance = humanizationLevel * 0.6f; // от 0.6 до 6 градусов
            
            // Увеличиваем неточность при усталости
            if (humanizationLevel >= 3 && isFatigued) {
                baseYawVariance *= 1.5f;
                basePitchVariance *= 1.3f;
            }
            
            targetYaw += (random.nextFloat() - 0.5f) * baseYawVariance;
            targetPitch += (random.nextFloat() - 0.5f) * basePitchVariance;
            
            // "Перестрелы" цели только при среднем и высоком уровне
            if (humanizationLevel >= 5) {
                int overshootChance = Math.max(25 - humanizationLevel, 10);
                if (random.nextInt(overshootChance) == 0) {
                    targetYaw += (random.nextFloat() - 0.5f) * (humanizationLevel * 2.0f);
                }
                if (random.nextInt(overshootChance + 5) == 0) {
                    targetPitch += (random.nextFloat() - 0.5f) * (humanizationLevel * 1.5f);
                }
            }
        }
        
        // Ограничиваем pitch
        targetPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f);
    }
    
    private static void updateSmoothRotation(ClientPlayerEntity player) {
        // Плавно поворачиваем к цели
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        
        // Базовая скорость поворота
        float baseRotationSpeed;
        if (humanizationLevel == 0) {
            baseRotationSpeed = ROTATION_SPEED * 1.5f; // быстрые повороты для робота
        } else {
            baseRotationSpeed = isFatigued ? ROTATION_SPEED * 0.6f : ROTATION_SPEED;
        }
        
        float currentRotationSpeed = baseRotationSpeed;
        
        // Добавляем вариативность только если уровень > 0
        if (humanizationLevel > 0) {
            float rotationSpeedVariance = random.nextFloat() * (humanizationLevel * 0.2f);
            currentRotationSpeed += rotationSpeedVariance;
            
            // "Рывки" мышью только при среднем и высоком уровне
            if (humanizationLevel >= 4) {
                int jerkChance = Math.max(60 - humanizationLevel * 4, 20);
                if (random.nextInt(jerkChance) == 0) {
                    currentRotationSpeed *= 1.5f + random.nextFloat() * (humanizationLevel * 0.2f);
                } else if (random.nextInt(jerkChance + 10) == 0) {
                    currentRotationSpeed *= 0.3f; // очень медленный поворот
                }
            }
        }
        
        // Асимметричная скорость поворота только при уровне > 1
        float yawSpeed = currentRotationSpeed;
        float pitchSpeed = humanizationLevel > 1 ? currentRotationSpeed * 0.8f : currentRotationSpeed;
        
        currentYaw += MathHelper.clamp(yawDiff, -yawSpeed, yawSpeed);
        currentPitch += MathHelper.clamp(pitchDiff, -pitchSpeed, pitchSpeed);
        
        // Микродрожание только при высоком уровне человечности
        if (humanizationLevel >= 6) {
            int tremorChance = Math.max(20 - humanizationLevel, 5);
            if (random.nextInt(tremorChance) == 0) {
                float tremorStrength = humanizationLevel * 0.1f;
                currentYaw += (random.nextFloat() - 0.5f) * tremorStrength;
                currentPitch += (random.nextFloat() - 0.5f) * tremorStrength * 0.6f;
            }
        }
        
        // Ограничиваем pitch
        currentPitch = MathHelper.clamp(currentPitch, -90.0f, 90.0f);
        
        // Применяем повороты
        player.setYaw(currentYaw);
        player.setPitch(currentPitch);
    }
    
    private static void startLookingAround() {
        isLookingAround = true;
        lookAroundDuration = 20 + random.nextInt(60); // 1-4 секунды
        
        // Случайное направление осмотра
        lookAroundYaw = currentYaw + (random.nextFloat() - 0.5f) * 120.0f; // ±60 градусов
    }
    
    private static void performLookAround(ClientPlayerEntity player) {
        if (lookAroundDuration > 0) {
            lookAroundDuration--;
            
            // Плавно поворачиваемся для осмотра
            targetYaw = lookAroundYaw;
            targetPitch = -10.0f + random.nextFloat() * 20.0f; // смотрим немного вверх-вниз
            
            if (lookAroundDuration == 0) {
                // Возвращаемся к исходному направлению
                targetYaw = currentYaw + (random.nextFloat() - 0.5f) * 10.0f;
                targetPitch = 0.0f;
                isLookingAround = false;
            }
        }
    }
    
    private static Direction getRightDirection(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }
    
    private static boolean hasPickaxe(ClientPlayerEntity player) {
        return getPickaxe(player) != null;
    }
    
    private static ItemStack getPickaxe(ClientPlayerEntity player) {
        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
        
        if (mainHand.getItem() instanceof PickaxeItem) {
            return mainHand;
        } else if (offHand.getItem() instanceof PickaxeItem) {
            return offHand;
        }
        return null;
    }
    
    private static void checkPickaxeDurability(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        ItemStack pickaxe = getPickaxe(player);
        if (pickaxe != null) {
            int durability = pickaxe.getMaxDamage() - pickaxe.getDamage();
            int maxDurability = pickaxe.getMaxDamage();
            double durabilityPercent = (double) durability / maxDurability;
            
            // Предупреждаем при 20% прочности
            if (durabilityPercent <= 0.2 && durabilityPercent > 0.1) {
                player.sendMessage(Text.literal("§6⚠ Внимание: Прочность кирки " + String.format("%.1f", durabilityPercent * 100) + "%"), false);
            }
            
            // Останавливаем при 10% прочности
            if (durabilityPercent <= 0.1) {
                stopDigging(client);
                player.sendMessage(Text.literal("§c⚠ Копание остановлено: кирка почти сломана!"), false);
                player.sendMessage(Text.literal("§cПрочность: " + durability + "/" + maxDurability + " (" + String.format("%.1f", durabilityPercent * 100) + "%)"), false);
            }
        }
    }
    
    // ============ СИСТЕМА БЕЗОПАСНОСТИ ============
    
    private static void loadWhitelist() {
        whitelistedPlayers.clear();
        Path whitelistFile = Paths.get("config", "whitelist.txt");
        
        try {
            // Создаем папку config если её нет
            Files.createDirectories(whitelistFile.getParent());
            
            // Создаем файл если его нет
            if (!Files.exists(whitelistFile)) {
                createDefaultWhitelist(whitelistFile);
            }
            
            // Читаем файл
            List<String> lines = Files.readAllLines(whitelistFile);
            for (String line : lines) {
                line = line.trim();
                // Игнорируем пустые строки и комментарии
                if (!line.isEmpty() && !line.startsWith("#")) {
                    whitelistedPlayers.add(line.toLowerCase());
                }
            }
            
            AutoDiggerMod.LOGGER.info("Загружен whitelist: " + whitelistedPlayers.size() + " игроков");
            
        } catch (IOException e) {
            AutoDiggerMod.LOGGER.error("Ошибка при загрузке whitelist: " + e.getMessage());
        }
    }
    
    private static void createDefaultWhitelist(Path file) throws IOException {
        String defaultContent = """
            # Список игроков-исключений (друзья, союзники)
            # Добавьте ники игроков, при виде которых бот НЕ должен останавливаться
            # Каждый ник на отдельной строке
            # Строки начинающиеся с # - комментарии
            
            # Примеры:
            # MyFriend123
            # BestAlly
            # TrustedPlayer
            
            # Добавьте своих друзей ниже:
            """;
        Files.write(file, defaultContent.getBytes());
    }
    
    private static boolean checkForDangerousPlayers(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return false;
        }
        
        Vec3d playerPos = client.player.getPos();
        
        // Получаем всех игроков в мире
        for (PlayerEntity player : client.world.getPlayers()) {
            // Пропускаем самого себя
            if (player == client.player) {
                continue;
            }
            
            // Проверяем расстояние
            double distance = player.getPos().distanceTo(playerPos);
            if (distance <= detectionRadius) {
                
                String playerName = player.getName().getString().toLowerCase();
                
                // Проверяем, есть ли игрок в whitelist
                if (!whitelistedPlayers.contains(playerName)) {
                    // ОПАСНЫЙ ИГРОК ОБНАРУЖЕН!
                    emergencyStop(client, player.getName().getString(), distance);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static void emergencyStop(MinecraftClient client, String playerName, double distance) {
        emergencyDisconnect(client, "Обнаружен игрок: " + playerName + " на расстоянии " + String.format("%.1f", distance) + " блоков");
    }
    
    private static void emergencyDisconnect(MinecraftClient client, String reason) {
        // Немедленно останавливаем копание
        isDigging = false;
        isLookingAround = false;
        
        // Логирование
        AutoDiggerMod.LOGGER.warn("EMERGENCY DISCONNECT: " + reason);
        
        if (client.player != null) {
            // Последнее сообщение перед отключением
            client.player.sendMessage(Text.literal("§c🚨 СИСТЕМА БЕЗОПАСНОСТИ 🚨"), false);
            client.player.sendMessage(Text.literal("§c⚠ " + reason), false);
            client.player.sendMessage(Text.literal("§c⚠ ОТКЛЮЧЕНИЕ ОТ СЕРВЕРА!"), false);
        }
        
        // Отключаемся от сервера через небольшую задержку
        Thread disconnectThread = new Thread(() -> {
            try {
                Thread.sleep(1000); // ждем 1 секунду чтобы сообщение успело отправиться
                client.execute(() -> {
                    if (client.world != null) {
                        client.world.disconnect();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
                 });
         disconnectThread.start();
    }
    
    // ============ СИСТЕМА КОНФИГУРАЦИИ ============
    
    private static void loadAllConfigs() {
        loadWhitelist();
        loadCommands();
        loadNearKeywords();
    }
    
    private static void loadCommands() {
        autoCommands.clear();
        Path commandsFile = Paths.get("config", "commands.txt");
        
        try {
            Files.createDirectories(commandsFile.getParent());
            
            if (!Files.exists(commandsFile)) {
                createDefaultCommands(commandsFile);
            }
            
            List<String> lines = Files.readAllLines(commandsFile);
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    autoCommands.add(line);
                }
            }
            
            AutoDiggerMod.LOGGER.info("Загружено команд: " + autoCommands.size());
            
        } catch (IOException e) {
            AutoDiggerMod.LOGGER.error("Ошибка при загрузке команд: " + e.getMessage());
        }
    }
    
    private static void createDefaultCommands(Path file) throws IOException {
        String defaultContent = """
            # Конфигурация автоматических команд
            # Каждая команда на отдельной строке
            # Команды выполняются каждые 10-20 секунд (случайно)
            # Строки с # - комментарии
            
            # Основные команды для серверов:
            /near
            /fix
            /feed
            
            # Дополнительные команды (раскомментируйте при необходимости):
            # /heal
            # /repair
            # /home
            # /spawn
            """;
        Files.write(file, defaultContent.getBytes());
    }
    
    private static void loadNearKeywords() {
        safeKeywords.clear();
        dangerKeywords.clear();
        Path keywordsFile = Paths.get("config", "near-keywords.txt");
        
        try {
            Files.createDirectories(keywordsFile.getParent());
            
            if (!Files.exists(keywordsFile)) {
                createDefaultKeywords(keywordsFile);
            }
            
            List<String> lines = Files.readAllLines(keywordsFile);
            boolean inSafeSection = false;
            boolean inDangerSection = false;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.equals("[safe_keywords]")) {
                    inSafeSection = true;
                    inDangerSection = false;
                    continue;
                }
                
                if (line.equals("[danger_keywords]")) {
                    inSafeSection = false;
                    inDangerSection = true;
                    continue;
                }
                
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (inSafeSection) {
                        safeKeywords.add(line.toLowerCase());
                    } else if (inDangerSection) {
                        dangerKeywords.add(line.toLowerCase());
                    }
                }
            }
            
            AutoDiggerMod.LOGGER.info("Загружено ключевых слов: " + safeKeywords.size() + " безопасных, " + dangerKeywords.size() + " опасных");
            
        } catch (IOException e) {
            AutoDiggerMod.LOGGER.error("Ошибка при загрузке ключевых слов: " + e.getMessage());
        }
    }
    
    private static void createDefaultKeywords(Path file) throws IOException {
        String defaultContent = """
            # Конфигурация ключевых слов для анализа команды /near
            # safe_keywords - если в ответе сервера есть эти слова = безопасно
            # danger_keywords - если в ответе есть эти слова = ОПАСНОСТЬ (срабатывает система безопасности)
            
            [safe_keywords]
            Ничего
            никого
            пусто
            no players
            nobody
            empty
            нет игроков
            
            [danger_keywords]
            рядом
            nearby
            player
            игрок
            found
            найден
            близко
            close
            """;
        Files.write(file, defaultContent.getBytes());
    }
    
    // ============ АВТОМАТИЧЕСКИЕ КОМАНДЫ ============
    
    private static void executeAutoCommand(MinecraftClient client) {
        if (autoCommands.isEmpty() || client.player == null) {
            return;
        }
        
        String command = autoCommands.get(random.nextInt(autoCommands.size()));
        
        // Особая обработка для команды /near
        if (command.equals("/near")) {
            waitingForNearResponse = true;
            nearCommandTime = System.currentTimeMillis();
        }
        
        // Отправляем команду
        client.player.networkHandler.sendChatMessage(command);
        AutoDiggerMod.LOGGER.info("Выполнена автоматическая команда: " + command);
    }
    
    // ============ СИСТЕМА ПИТАНИЯ ============
    
    private static void manageFood(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        
        ClientPlayerEntity player = client.player;
        
        // Проверяем голод
        if (player.getHungerManager().getFoodLevel() < 15) {
            // Проверяем что в руке
            ItemStack mainHand = player.getMainHandStack();
            
            if (isFood(mainHand)) {
                // Если в руке еда - едим
                eatFood(client);
            } else {
                // Ищем еду в инвентаре и перекладываем в руку
                findAndEquipFood(player);
            }
        }
    }
    
    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        
        return stack.getItem().isFood();
    }
    
    private static void eatFood(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        
        // Симулируем нажатие правой кнопки мыши для еды
        client.interactionManager.interactItem(client.player, client.player.getActiveHand());
        AutoDiggerMod.LOGGER.info("Ем еду");
    }
    
    private static void findAndEquipFood(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        
        // Ищем еду в инвентаре
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (isFood(stack)) {
                // Найдена еда - перемещаем в активный слот
                int currentSlot = inventory.selectedSlot;
                
                // Если еда не в хотбаре - меняем местами
                if (i >= 9) {
                    // Swap с текущим активным слотом
                    ItemStack currentItem = inventory.getStack(currentSlot);
                    inventory.setStack(currentSlot, stack);
                    inventory.setStack(i, currentItem);
                } else {
                    // Еда уже в хотбаре - просто выбираем слот
                    inventory.selectedSlot = i;
                }
                
                AutoDiggerMod.LOGGER.info("Переложена еда в руку: " + stack.getItem().getName().getString());
                return;
            }
        }
        
                 AutoDiggerMod.LOGGER.warn("Еда не найдена в инвентаре!");
    }
    
    // ============ СИСТЕМА УМНОГО КОПАНИЯ В МЕЗЕ ============
    
    private static boolean isInMesaBiome(MinecraftClient client, BlockPos pos) {
        if (client.world == null) return false;
        
        Biome biome = client.world.getBiome(pos).value();
        
        // Проверяем различные типы мезы
        return client.world.getBiome(pos).isIn(BiomeTags.IS_BADLANDS);
    }
    
    private static void checkCurrentBiome(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        
        BlockPos pos = client.player.getBlockPos();
        Biome biome = client.world.getBiome(pos).value();
        boolean isMesa = isInMesaBiome(client, pos);
        
        client.player.sendMessage(Text.literal("§b=== Проверка биома ==="), false);
        client.player.sendMessage(Text.literal("§bТекущая позиция: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        client.player.sendMessage(Text.literal("§bЭто меза: " + (isMesa ? "§aДА" : "§cНЕТ")), false);
        
        if (!isMesa) {
            client.player.sendMessage(Text.literal("§eРекомендация: найдите биом мезы для эффективной добычи золота"), false);
        }
    }
    
    private static void updatePreferredDirections(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        
        BlockPos currentPos = client.player.getBlockPos();
        preferredDirections.clear();
        
        // Проверяем все направления на наличие мезы
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = currentPos.offset(dir, 10); // проверяем на 10 блоков вперед
            if (isInMesaBiome(client, checkPos)) {
                preferredDirections.add(dir);
            }
        }
        
        // Проверяем вертикальные направления
        BlockPos upPos = currentPos.up(5);
        BlockPos downPos = currentPos.down(5);
        
        if (isInMesaBiome(client, upPos)) {
            preferredDirections.add(Direction.UP);
        }
        if (isInMesaBiome(client, downPos)) {
            preferredDirections.add(Direction.DOWN);
        }
        
        AutoDiggerMod.LOGGER.info("Обновлены предпочтительные направления: " + preferredDirections.size());
    }
    
    private static void findBetterDirection(MinecraftClient client) {
        if (client.player == null || preferredDirections.isEmpty()) return;
        
        // Выбираем случайное предпочтительное направление
        Direction newDirection = preferredDirections.get(random.nextInt(preferredDirections.size()));
        
        // Плавно меняем направление копания
        if (newDirection == Direction.UP || newDirection == Direction.DOWN) {
            // Вертикальное движение - меняем высоту
            int yChange = newDirection == Direction.UP ? 2 : -2;
            startPos = startPos.up(yChange);
            client.player.sendMessage(Text.literal("§eИзменяю высоту копания для оставания в мезе"), false);
        } else {
            // Горизонтальное движение - меняем направление
            facingDirection = newDirection;
            currentCorridorBlocks = 0; // начинаем новый коридор
            client.player.sendMessage(Text.literal("§eИзменяю направление копания для оставания в мезе"), false);
        }
    }
    
    // ============ СИСТЕМА ДЕТЕКЦИИ И ДОБЫЧИ ЗОЛОТА ============
    
    private static void scanForGold(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        scanForGoldAroundPosition(client, playerPos);
    }
    
    private static void scanForGoldAroundPosition(MinecraftClient client, BlockPos centerPos) {
        if (client.world == null) return;
        
        int newGoldFound = 0;
        
        // Сканируем в радиусе goldScanRadius
        for (int x = -goldScanRadius; x <= goldScanRadius; x++) {
            for (int y = -goldScanRadius; y <= goldScanRadius; y++) {
                for (int z = -goldScanRadius; z <= goldScanRadius; z++) {
                    BlockPos checkPos = centerPos.add(x, y, z);
                    Block block = client.world.getBlockState(checkPos).getBlock();
                    
                    // Проверяем на золотую руду
                    if (isGoldOre(block)) {
                        // Проверяем, не добавляли ли мы уже этот блок
                        if (!detectedGoldOres.contains(checkPos)) {
                            detectedGoldOres.add(checkPos);
                            newGoldFound++;
                        }
                    }
                }
            }
        }
        
        if (newGoldFound > 0) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§6⭐ Обнаружено золота: " + newGoldFound + " блоков!"), false);
                client.player.sendMessage(Text.literal("§6Всего в очереди: " + detectedGoldOres.size() + " блоков"), false);
            }
            
            // Начинаем добычу, если не активна
            if (!miningGold) {
                startGoldMining(client);
            }
        }
    }
    
    private static boolean isGoldOre(Block block) {
        return block == Blocks.GOLD_ORE || 
               block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.NETHER_GOLD_ORE ||
               block == Blocks.RAW_GOLD_BLOCK ||
               block == Blocks.GOLD_BLOCK;
    }
    
    private static void startGoldMining(MinecraftClient client) {
        if (detectedGoldOres.isEmpty()) return;
        
        miningGold = true;
        currentGoldIndex = 0;
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§6⛏ Начинаю добычу золота!"), false);
            client.player.sendMessage(Text.literal("§6Найдено блоков: " + detectedGoldOres.size()), false);
        }
        
        AutoDiggerMod.LOGGER.info("Начата добыча золота: " + detectedGoldOres.size() + " блоков");
    }
    
    private static void mineDetectedGold(MinecraftClient client) {
        if (detectedGoldOres.isEmpty() || currentGoldIndex >= detectedGoldOres.size()) {
            finishGoldMining(client);
            return;
        }
        
        BlockPos goldPos = detectedGoldOres.get(currentGoldIndex);
        
        // Проверяем, что блок все еще золото
        if (client.world != null && !isGoldOre(client.world.getBlockState(goldPos).getBlock())) {
            // Блок уже не золото, переходим к следующему
            currentGoldIndex++;
            return;
        }
        
        // Копаем золото с плавными поворотами
        Vec3d goldCenter = Vec3d.ofCenter(goldPos);
        setTargetLook(goldCenter, client.player.getPos());
        
        // Двигаемся к золоту
        movePlayerToTarget(client.player, goldPos);
        
        // Копаем блок
        if (client.interactionManager != null) {
            client.interactionManager.attackBlock(goldPos, Direction.UP);
            
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§6⛏ Добываю золото " + (currentGoldIndex + 1) + "/" + detectedGoldOres.size()), false);
            }
        }
        
        currentGoldIndex++;
    }
    
    private static void finishGoldMining(MinecraftClient client) {
        miningGold = false;
        detectedGoldOres.clear();
        currentGoldIndex = 0;
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§6✅ Добыча золота завершена!"), false);
            client.player.sendMessage(Text.literal("§aВозвращаюсь к обычному копанию"), false);
        }
        
        AutoDiggerMod.LOGGER.info("Добыча золота завершена");
    }
    
    // ============ СИСТЕМА ПОЛНОГО КОПАНИЯ ЖИЛ РУДЫ (VEIN MINING) ============
    
    /**
     * Сканирование окрестностей в поисках жил руды
     */
    private static void scanForVeins(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        
        // Избегаем повторного сканирования той же области
        if (lastVeinStartPos != null && playerPos.isWithinDistance(lastVeinStartPos, veinScanRadius / 2)) {
            return;
        }
        
        // Сканируем в радиусе veinScanRadius
        for (int x = -veinScanRadius; x <= veinScanRadius; x++) {
            for (int y = -veinScanRadius; y <= veinScanRadius; y++) {
                for (int z = -veinScanRadius; z <= veinScanRadius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = client.world.getBlockState(checkPos).getBlock();
                    
                    // Найдена руда!
                    if (isVeinOre(block)) {
                        // Проверяем, не обрабатывали ли мы уже эту область
                        if (scannedBlocks.contains(checkPos)) {
                            continue;
                        }
                        
                        // Начинаем поиск всей жилы
                        startVeinMining(client, checkPos, block);
                        return;
                    }
                }
            }
        }
        
        lastVeinStartPos = playerPos;
    }
    
    /**
     * Запуск добычи жилы руды
     */
    private static void startVeinMining(MinecraftClient client, BlockPos startPos, Block oreType) {
        veinBlocks.clear();
        scannedBlocks.clear();
        currentVeinIndex = 0;
        currentVeinOreType = oreType;
        
        // Ищем все блоки жилы
        findVeinBlocks(client, startPos, oreType);
        
        if (veinBlocks.isEmpty()) {
            return;
        }
        
        miningVein = true;
        
        if (client.player != null) {
            String oreName = getOreName(oreType);
            client.player.sendMessage(Text.literal("§6⛏ НАЙДЕНА ЖИЛА РУДЫ!"), false);
            client.player.sendMessage(Text.literal("§6Тип: " + oreName), false);
            client.player.sendMessage(Text.literal("§6Размер: " + veinBlocks.size() + " блоков"), false);
            client.player.sendMessage(Text.literal("§6Начинаю полное извлечение жилы..."), false);
        }
        
        AutoDiggerMod.LOGGER.info("Начата добыча жилы " + oreType + ": " + veinBlocks.size() + " блоков");
    }
    
    /**
     * Поиск всех блоков жилы с помощью BFS (поиск в ширину)
     */
    private static void findVeinBlocks(MinecraftClient client, BlockPos startPos, Block oreType) {
        if (client.world == null) return;
        
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.offer(startPos);
        visited.add(startPos);
        
        // Направления поиска (все 26 направлений включая диагонали)
        List<BlockPos> directions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    directions.add(new BlockPos(x, y, z));
                }
            }
        }
        
        while (!queue.isEmpty() && veinBlocks.size() < maxVeinSize) {
            BlockPos currentPos = queue.poll();
            Block currentBlock = client.world.getBlockState(currentPos).getBlock();
            
            // Проверяем, что это тот же тип руды
            if (isSameVeinOre(currentBlock, oreType)) {
                veinBlocks.add(currentPos);
                scannedBlocks.add(currentPos);
                
                // Проверяем все соседние блоки
                for (BlockPos dir : directions) {
                    BlockPos neighborPos = currentPos.add(dir);
                    
                    if (!visited.contains(neighborPos)) {
                        visited.add(neighborPos);
                        Block neighborBlock = client.world.getBlockState(neighborPos).getBlock();
                        
                        if (isSameVeinOre(neighborBlock, oreType)) {
                            queue.offer(neighborPos);
                        }
                    }
                }
            }
        }
        
        // Сортируем блоки по расстоянию от игрока для оптимальной добычи
        BlockPos playerPos = client.player.getBlockPos();
        veinBlocks.sort((a, b) -> {
            double distA = a.getSquaredDistance(playerPos);
            double distB = b.getSquaredDistance(playerPos);
            return Double.compare(distA, distB);
        });
    }
    
    /**
     * Добыча блоков жилы
     */
    private static void mineVeinBlocks(MinecraftClient client) {
        if (veinBlocks.isEmpty() || currentVeinIndex >= veinBlocks.size()) {
            finishVeinMining(client);
            return;
        }
        
        BlockPos veinPos = veinBlocks.get(currentVeinIndex);
        
        // Проверяем, что блок все еще является рудой
        if (client.world != null && !isSameVeinOre(client.world.getBlockState(veinPos).getBlock(), currentVeinOreType)) {
            // Блок уже не руда, переходим к следующему
            currentVeinIndex++;
            return;
        }
        
        // Устанавливаем направление взгляда на блок
        Vec3d veinCenter = Vec3d.ofCenter(veinPos);
        setTargetLook(veinCenter, client.player.getPos());
        
        // Двигаемся к блоку
        movePlayerToTarget(client.player, veinPos);
        
        // Копаем блок с учетом человечности
        if (client.interactionManager != null) {
            // Промахи и вариативность как в обычном копании
            if (humanizationLevel >= 3) {
                int missChance = Math.max(50 - humanizationLevel * 3, 10);
                if (random.nextInt(missChance) == 0) {
                    currentVeinIndex++;
                    return; // пропускаем удар
                }
            }
            
            // Направление атаки
            Direction attackDirection = humanizationLevel == 0 ? Direction.UP : 
                Direction.values()[random.nextInt(Direction.values().length)];
            
            // Количество ударов
            int hitCount = humanizationLevel == 0 ? 1 : (1 + random.nextInt(Math.min(humanizationLevel / 3 + 1, 3)));
            
            for (int hit = 0; hit < hitCount; hit++) {
                client.interactionManager.attackBlock(veinPos, attackDirection);
                
                if (humanizationLevel > 0 && hit > 0 && random.nextInt(3) == 0) {
                    try {
                        Thread.sleep(5 + random.nextInt(humanizationLevel * 5));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // Показываем прогресс каждые 5 блоков
        if (client.player != null && (currentVeinIndex + 1) % 5 == 0) {
            int progress = currentVeinIndex + 1;
            int total = veinBlocks.size();
            client.player.sendMessage(Text.literal("§6⛏ Добыча жилы: " + progress + "/" + total + " (" + (progress * 100 / total) + "%)"), false);
        }
        
        currentVeinIndex++;
    }
    
    /**
     * Завершение добычи жилы
     */
    private static void finishVeinMining(MinecraftClient client) {
        int minedBlocks = currentVeinIndex;
        
        miningVein = false;
        veinBlocks.clear();
        currentVeinIndex = 0;
        currentVeinOreType = null;
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§6✅ ЖИЛА ПОЛНОСТЬЮ ИЗВЛЕЧЕНА!"), false);
            client.player.sendMessage(Text.literal("§6Добыто блоков: " + minedBlocks), false);
            client.player.sendMessage(Text.literal("§aВозвращаюсь к обычному копанию"), false);
        }
        
        AutoDiggerMod.LOGGER.info("Добыча жилы завершена: " + minedBlocks + " блоков");
    }
    
    /**
     * Проверка, является ли блок рудой для жилы
     */
    private static boolean isVeinOre(Block block) {
        return VEIN_ORES.contains(block);
    }
    
    /**
     * Проверка, относится ли блок к той же жиле
     */
    private static boolean isSameVeinOre(Block block, Block oreType) {
        // Разные варианты одной руды считаются одной жилой
        return isSameOreFamily(block, oreType);
    }
    
    /**
     * Проверка семейства руд (обычная и deepslate версии)
     */
    private static boolean isSameOreFamily(Block block1, Block block2) {
        if (block1 == block2) return true;
        
        // Железо
        if ((block1 == Blocks.IRON_ORE || block1 == Blocks.DEEPSLATE_IRON_ORE) &&
            (block2 == Blocks.IRON_ORE || block2 == Blocks.DEEPSLATE_IRON_ORE)) {
            return true;
        }
        
        // Уголь
        if ((block1 == Blocks.COAL_ORE || block1 == Blocks.DEEPSLATE_COAL_ORE) &&
            (block2 == Blocks.COAL_ORE || block2 == Blocks.DEEPSLATE_COAL_ORE)) {
            return true;
        }
        
        // Золото
        if ((block1 == Blocks.GOLD_ORE || block1 == Blocks.DEEPSLATE_GOLD_ORE || block1 == Blocks.NETHER_GOLD_ORE) &&
            (block2 == Blocks.GOLD_ORE || block2 == Blocks.DEEPSLATE_GOLD_ORE || block2 == Blocks.NETHER_GOLD_ORE)) {
            return true;
        }
        
        // Медь
        if ((block1 == Blocks.COPPER_ORE || block1 == Blocks.DEEPSLATE_COPPER_ORE) &&
            (block2 == Blocks.COPPER_ORE || block2 == Blocks.DEEPSLATE_COPPER_ORE)) {
            return true;
        }
        
        // Лазурит
        if ((block1 == Blocks.LAPIS_ORE || block1 == Blocks.DEEPSLATE_LAPIS_ORE) &&
            (block2 == Blocks.LAPIS_ORE || block2 == Blocks.DEEPSLATE_LAPIS_ORE)) {
            return true;
        }
        
        // Редстоун
        if ((block1 == Blocks.REDSTONE_ORE || block1 == Blocks.DEEPSLATE_REDSTONE_ORE) &&
            (block2 == Blocks.REDSTONE_ORE || block2 == Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return true;
        }
        
        // Алмазы
        if ((block1 == Blocks.DIAMOND_ORE || block1 == Blocks.DEEPSLATE_DIAMOND_ORE) &&
            (block2 == Blocks.DIAMOND_ORE || block2 == Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return true;
        }
        
        // Изумруды
        if ((block1 == Blocks.EMERALD_ORE || block1 == Blocks.DEEPSLATE_EMERALD_ORE) &&
            (block2 == Blocks.EMERALD_ORE || block2 == Blocks.DEEPSLATE_EMERALD_ORE)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Получение читаемого названия руды
     */
    private static String getOreName(Block block) {
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return "Железная руда";
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return "Угольная руда";
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return "Золотая руда";
        if (block == Blocks.NETHER_GOLD_ORE) return "Незерская золотая руда";
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return "Медная руда";
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return "Лазуритовая руда";
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return "Красная руда";
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return "Алмазная руда";
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return "Изумрудная руда";
        if (block == Blocks.NETHER_QUARTZ_ORE) return "Кварцевая руда";
        if (block == Blocks.ANCIENT_DEBRIS) return "Древние обломки";
        
        return "Неизвестная руда";
    }
    
    /**
     * Показать информацию о системе vein mining
     */
    private static void showVeinInfo(MinecraftClient client) {
        if (client.player == null) return;
        
        client.player.sendMessage(Text.literal("§6=== СИСТЕМА ПОЛНОГО КОПАНИЯ ЖИЛ ==="), false);
        client.player.sendMessage(Text.literal("§bСостояние: " + (veinMiningEnabled ? "§aВКЛ" : "§cВЫКЛ")), false);
        client.player.sendMessage(Text.literal("§bРадиус поиска: " + veinScanRadius + " блоков"), false);
        client.player.sendMessage(Text.literal("§bМакс. размер жилы: " + maxVeinSize + " блоков"), false);
        
        if (miningVein) {
            client.player.sendMessage(Text.literal("§6▬▬▬ АКТИВНАЯ ДОБЫЧА ▬▬▬"), false);
            client.player.sendMessage(Text.literal("§6Тип руды: " + getOreName(currentVeinOreType)), false);
            client.player.sendMessage(Text.literal("§6Прогресс: " + currentVeinIndex + "/" + veinBlocks.size()), false);
            int percent = veinBlocks.size() > 0 ? (currentVeinIndex * 100 / veinBlocks.size()) : 0;
            client.player.sendMessage(Text.literal("§6Завершено: " + percent + "%"), false);
        } else {
            client.player.sendMessage(Text.literal("§7Сейчас добыча жил неактивна"), false);
        }
        
        client.player.sendMessage(Text.literal("§e▬▬▬ ПОДДЕРЖИВАЕМЫЕ РУДЫ ▬▬▬"), false);
        client.player.sendMessage(Text.literal("§7• Железо, Уголь, Золото, Медь"), false);
        client.player.sendMessage(Text.literal("§7• Лазурит, Редстоун, Алмазы, Изумруды"), false);
        client.player.sendMessage(Text.literal("§7• Кварц (Незер), Древние обломки"), false);
        client.player.sendMessage(Text.literal("§7• Автоопределение жил обычной и deepslate руды"), false);
    }
} 