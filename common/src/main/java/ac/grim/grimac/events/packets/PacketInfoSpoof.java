package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.data.TrackerData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemEnchantments;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentType;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTShort;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleColorData;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import ac.grim.grimac.utils.team.TeamHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PacketInfoSpoof extends PacketListenerAbstract {

    private static volatile boolean spoofHealth;
    private static volatile boolean spoofAbsorption;
    private static volatile boolean spoofEffects;
    private static volatile boolean spoofMaxHealth;
    private static volatile boolean spoofHealthScore;
    private static volatile boolean spoofMobHealth;
    private static volatile Set<String> healthObjectives = Set.of();
    private static volatile boolean strictScores;
    private static volatile boolean spoofAttributes;
    private static volatile boolean spoofGroundItems;
    private static volatile boolean namePlainItems;
    private static volatile boolean namePlainEquipment;
    private static volatile boolean namePlainMobs;
    private static volatile boolean spoofEquipment;
    private static volatile boolean stripEnchantments;
    private static volatile boolean stripPotions;
    private static volatile boolean stripDurability;
    private static volatile boolean stripCount;
    private static volatile boolean stripItemAttributes;
    private static volatile boolean spoofBrand;
    private static volatile byte[] brandPayload = new byte[0];
    private static volatile boolean spoofMobNames;
    private static volatile boolean replaceItemNames;
    private static volatile boolean replaceMobNames;
    private static volatile Names itemNames = Names.EMPTY;
    private static volatile Names mobNames = Names.EMPTY;
    private static volatile boolean keepParticles = true;
    private static volatile int particleColor = 0x385DC6;
    private static volatile List<Particle<?>> neutralParticles = List.of();
    private static volatile float healthValue = 20f;
    private static volatile float healthSpread = 6f;
    private static volatile boolean fakeDamage;

    private static final String SILENT_TEAM = "gg_silent";
    private static final Map<UUID, Map<Integer, String>> silenced = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Float>> peakHealth = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Float>> visibleMax = new ConcurrentHashMap<>();
    private static final long HEALTH_SALT = ThreadLocalRandom.current().nextLong();
    private static final Map<UUID, Walk> walks = new ConcurrentHashMap<>();
    private static final Set<String> harmlessScores = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> scoreMisses = new ConcurrentHashMap<>();
    private static final Set<UUID> silencedTeams = ConcurrentHashMap.newKeySet();

    private static final EnchantmentType DECOY_ENCHANTMENT = EnchantmentTypes.UNBREAKING;

    // Vanilla base values bit for bit - a plain 0.1 instead of the widened float the server sends is a tell.
    private static final Map<String, Double> NEUTRAL_ATTRIBUTES = Map.of(
            "movement_speed", 0.10000000149011612,
            "attack_damage", 1.0,
            "armor", 0.0,
            "armor_toughness", 0.0,
            "knockback_resistance", 0.0);

    public PacketInfoSpoof() {
        // HIGHEST so grim's own listeners read the real values first. isPreVia is named for the serverbound
        // side: outbound it lands after via's encoder, already in the receiver's format.
        super(PacketListenerPriority.HIGHEST);
    }

    @Override
    public boolean isPreVia() {
        return true;
    }

    public static void reload(ConfigManager config) {
        spoofHealth = config.getBooleanElse("spoof.entity-health", false);
        spoofAbsorption = config.getBooleanElse("spoof.absorption", false);
        spoofEffects = config.getBooleanElse("spoof.potion-effects", false);
        spoofMaxHealth = config.getBooleanElse("spoof.max-health", false);
        spoofHealthScore = config.getBooleanElse("spoof.health-scoreboard", false);
        spoofMobHealth = config.getBooleanElse("spoof.mob-health", false);
        Set<String> objectives = new java.util.HashSet<>();
        for (String name : config.getStringListElse("spoof.health-scoreboard-objectives", List.of())) {
            objectives.add(name.toLowerCase());
        }
        healthObjectives = Set.copyOf(objectives);
        strictScores = config.getBooleanElse("spoof.health-scoreboard-strict", false);
        harmlessScores.clear();
        scoreMisses.clear();
        spoofAttributes = config.getBooleanElse("spoof.other-attributes", false);
        spoofGroundItems = config.getBooleanElse("spoof.ground-item-names", false);
        namePlainItems = config.getBooleanElse("spoof.name-plain-items", false);
        namePlainEquipment = config.getBooleanElse("spoof.name-plain-equipment", false);
        namePlainMobs = config.getBooleanElse("spoof.name-plain-mobs", false);
        spoofEquipment = config.getBooleanElse("spoof.equipment-names", false);
        stripEnchantments = config.getBooleanElse("spoof.strip-enchantments", false);
        stripPotions = config.getBooleanElse("spoof.potion-items", false);
        stripDurability = config.getBooleanElse("spoof.equipment-durability", false);
        stripCount = config.getBooleanElse("spoof.equipment-count", false);
        stripItemAttributes = config.getBooleanElse("spoof.item-attributes", false);
        spoofBrand = config.getBooleanElse("spoof.server-brand", false);
        brandPayload = brandPayload(config.getStringElse("spoof.server-brand-value", "vanilla"));
        spoofMobNames = config.getBooleanElse("spoof.hidden-mob-names", false);
        replaceItemNames = config.getBooleanElse("spoof.replace-item-names", false);
        replaceMobNames = config.getBooleanElse("spoof.replace-mob-names", false);
        boolean colors = config.getBooleanElse("spoof.parse-colors", false);
        itemNames = Names.of(config.getStringListElse("spoof-item-names", List.of()), colors);
        mobNames = Names.of(config.getStringListElse("spoof-mob-names", List.of()), colors);
        keepParticles = config.getBooleanElse("spoof.keep-particles", true);
        particleColor = config.getIntElse("spoof.particle-color", 0x385DC6);
        neutralParticles = List.of(new Particle<>(ParticleTypes.ENTITY_EFFECT, new ParticleColorData(particleColor)));
        // Clamped above zero: the client reads health <= 0 as dead and stops treating the player as pushable.
        healthValue = (float) Math.max(1.0, Math.min(1024.0, config.getDoubleElse("spoof.health-value", 20.0)));
        healthSpread = (float) Math.max(0.0, Math.min(healthValue - 1.0, config.getDoubleElse("spoof.health-spread", 6.0)));
        fakeDamage = config.getBooleanElse("spoof.health-fake-damage", false);
        // Unconditional: health-value or max-health may have moved, and a walk keeps the old ceiling.
        walks.clear();
        visibleMax.clear();
    }


    // Indices shift as fields are added to Entity: no-gravity in 1.10, pose in 1.14, ticks-frozen in 1.17.
    private static int healthIndex(ClientVersion version) {
        if (version.isNewerThanOrEquals(ClientVersion.V_1_17)) return 9;
        if (version.isNewerThanOrEquals(ClientVersion.V_1_14)) return 8;
        if (version.isNewerThanOrEquals(ClientVersion.V_1_10)) return 7;
        return 6;
    }

    // A viewer who is not being lied to sees the real world, including in their own command completion.
    public static boolean spoofExempt(User user) {
        GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        return receiver != null && receiver.noSpoofPermission;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (spoofExempt(event.getUser())) return;
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            if (!spoofHealth && !spoofAbsorption && !spoofEffects && !itemStrippingOn()
                    && !spoofMobNames && !spoofMobHealth) return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;

            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            int entityId = wrapper.getEntityId();

            // compensatedEntities learns of an entity a transaction later and would miss freshly spawned ones.
            TrackerData tracked = receiver.compensatedEntities.serverPositionsMap.get(entityId);
            EntityType type = tracked != null ? tracked.getEntityType() : null;
            if (type == null) return;

            List<EntityData<?>> metadata = wrapper.getEntityMetadata();
            boolean player = type == EntityTypes.PLAYER;
            boolean droppedItem = type == EntityTypes.ITEM;

            boolean doPlayer = player && (spoofHealth || spoofAbsorption || spoofEffects)
                    && ownHealthSafe(receiver, entityId);
            boolean doItem = droppedItem && itemStrippingOn();
            // Index 3 is the custom-name-visible flag: a tag the server wants everyone to read wins over ours.
            if (namePlainMobs && Boolean.TRUE.equals(valueAt(metadata, 3))) unmute(receiver, entityId);

            // Wolf tail droop, iron golem cracks and the wither armour overlay are drawn from health.
            boolean doMobHealth = spoofMobHealth && !player && !droppedItem
                    && type != EntityTypes.WOLF && type != EntityTypes.IRON_GOLEM && type != EntityTypes.WITHER
                    && EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY)
                    && ownHealthSafe(receiver, entityId);
            boolean doMobName = spoofMobNames && !player && !droppedItem
                    && EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY)
                    && Boolean.FALSE.equals(valueAt(metadata, 3));
            if (!doPlayer && !doItem && !doMobName && !doMobHealth) return;

            List<EntityData<?>> rewritten = null;
            int healthIndex = healthIndex(receiver.getClientVersion());
            boolean legacyJson = receiver.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13);

            for (int i = 0; i < metadata.size(); i++) {
                EntityData<?> data = metadata.get(i);
                Object value = data.getValue();
                Object replacement = null;

                if (doPlayer && value instanceof Float number) {
                    if (data.getIndex() == healthIndex) {
                        // The client derives death from health <= 0, so a real 0 has to pass through untouched.
                        if (spoofHealth && number > 0) {
                            UUID target = targetUuid(receiver, entityId);
                            float fake = fakeHealth(target, visibleMaxFor(receiver, entityId));
                            if (fakeDamage && target != null) fake = walked(target, number, fake);
                            if (number != fake) replacement = fake;
                        }
                    } else if (spoofAbsorption && number != 0f) {
                        // Health and absorption are the only floats on Player, so any other one is absorption.
                        replacement = 0f;
                    }
                } else if (doPlayer && spoofEffects && data.getIndex() == healthIndex + 1) {
                    // Effects sit directly after health on every version. Emptying it also kills the swirls,
                    // which the client still draws for invisible players, so one neutral particle stays.
                    if (value instanceof Integer color) {
                        if (color != 0) replacement = keepParticles ? particleColor : 0;
                    } else if (value instanceof List<?> particles && !particles.isEmpty()) {
                        replacement = keepParticles ? neutralParticles : Collections.emptyList();
                    }
                } else if (doMobHealth && data.getIndex() == healthIndex && value instanceof Float number) {
                    // Max health would be the cleaner source, but it is not known the tick the mob spawns.
                    if (number > 0) {
                        Float peak = peakHealth.computeIfAbsent(receiver.uuid, k -> new ConcurrentHashMap<>())
                                .merge(entityId, number, Math::max);
                        if (peak != null && !peak.equals(number)) replacement = peak;
                    }
                } else if (doMobName && data.getIndex() == 2) {
                    int pick = pickIndex(mobNames, replaceMobNames);
                    // A fake name is only safe if the tag can be silenced too, or honest players read it on hover.
                    if (pick >= 0 && !silence(receiver, entityId, event)) pick = -1;
                    if (value instanceof Optional<?> name && name.isPresent()) {
                        replacement = pick < 0 ? Optional.empty()
                                : Optional.of(name.get() instanceof String ? mobNames.json.get(pick) : mobNames.component.get(pick));
                    } else if (value instanceof String name && !name.isEmpty()) {
                        replacement = pick < 0 ? "" : mobNames.legacy.get(pick);
                    }
                } else if (doItem && value instanceof ItemStack item) {
                    replacement = strip(item, spoofGroundItems, namePlainItems, false, false, receiver.getClientVersion());
                }

                if (replacement == null) continue;
                // The wrapper's list is shared with every other listener in this dispatch, so copy it.
                if (rewritten == null) rewritten = new ArrayList<>(metadata);
                rewritten.set(i, copyWith(data, replacement));
            }

            if (rewritten != null) {
                wrapper.setEntityMetadata(rewritten);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            if (!spoofMaxHealth && !spoofAttributes && !spoofHealth) return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;

            WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
            int entityId = wrapper.getEntityId();
            if (!ownHealthSafe(receiver, entityId)) return;

            TrackerData tracked = receiver.compensatedEntities.serverPositionsMap.get(entityId);
            if (tracked == null || tracked.getEntityType() != EntityTypes.PLAYER) return;

            List<WrapperPlayServerUpdateAttributes.Property> properties = wrapper.getProperties();
            List<WrapperPlayServerUpdateAttributes.Property> rewritten = null;

            for (int i = 0; i < properties.size(); i++) {
                WrapperPlayServerUpdateAttributes.Property property = properties.get(i);
                boolean maxHealth = isMaxHealth(property.getAttribute());
                double value;
                if (spoofMaxHealth && maxHealth) {
                    value = healthValue;
                } else {
                    Double neutral = spoofAttributes ? neutralValue(property.getAttribute()) : null;
                    if (neutral == null) {
                        if (maxHealth) rememberMax(receiver, entityId, (float) property.getValue());
                        continue;
                    }
                    value = neutral;
                }
                if (maxHealth) rememberMax(receiver, entityId, (float) value);
                if (property.getValue() == value && property.getModifiers().isEmpty()) continue;

                if (rewritten == null) rewritten = new ArrayList<>(properties);
                rewritten.set(i, new WrapperPlayServerUpdateAttributes.Property(
                        property.getAttribute(), value, Collections.emptyList()));
            }

            if (rewritten != null) {
                wrapper.setProperties(rewritten);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.UPDATE_SCORE) {
            if (!spoofHealthScore) return;

            WrapperPlayServerUpdateScore wrapper = new WrapperPlayServerUpdateScore(event);
            if (wrapper.getAction() != WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM) return;

            String owner = wrapper.getEntityName();
            UserProfile profile = event.getUser().getProfile();
            if (owner == null || owner.equals(profile == null ? null : profile.getName())) return;

            String objective = wrapper.getObjectiveName();
            String criterion = GrimAPI.INSTANCE.getPlatformServer().getObjectiveCriterion(objective);
            // The criterion never reaches the client, so health in a dummy objective looks the same on the
            // wire as anything else. Name matching catches the ones we are told about.
            boolean health = spoofHealthScore
                    && ("health".equals(criterion) || healthObjectives.contains(objective.toLowerCase()));

            if (spoofHealthScore && strictScores && !health) {
                health = !provenHarmless(objective, owner, wrapper.getValue().orElse(Integer.MIN_VALUE));
            }

            if (!health) return;

            PlatformPlayer target = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromName(owner);
            UUID id = target == null ? null : target.getUniqueId();
            float shown = fakeHealth(id, healthValue);
            Walk walk = fakeDamage && id != null ? walks.get(id) : null;
            int spoofed = Math.round(walk == null ? shown : walk.shown);
            if (wrapper.getValue().orElse(Integer.MIN_VALUE) == spoofed) return;
            wrapper.setValue(Optional.of(spoofed));
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            if (!namePlainMobs || mobNames.component.isEmpty()) return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;

            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
            EntityType type = spawn.getEntityType();
            // Armour stands are how servers build holograms - a name of ours would fight theirs.
            if (type == EntityTypes.PLAYER || type == EntityTypes.ARMOR_STAND
                    || !EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY)) return;

            UUID uuid = spawn.getUUID().orElse(null);
            if (uuid == null) return; // pre-1.9 spawn packets carry none, and a team needs one

            int spawned = spawn.getEntityId();
            String member = uuid.toString();
            if (silenced.computeIfAbsent(receiver.uuid, k -> new ConcurrentHashMap<>()).putIfAbsent(spawned, member) != null) {
                return;
            }

            int pick = ThreadLocalRandom.current().nextInt(mobNames.component.size());
            boolean adventure = receiver.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13);
            // Not a post task: those run inside the encoder before the spawn is written. +1 because the
            // current transaction is often already acknowledged when entities stream in on join.
            receiver.latencyUtils.addRealTimeTask(receiver.lastTransactionSent.get() + 1, () -> {
                mute(receiver, member); // first, so the name never exists on a mob that still shows tags
                receiver.user.sendPacketSilently(new WrapperPlayServerEntityMetadata(spawned,
                        List.of(adventure
                                ? new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(mobNames.component.get(pick)))
                                : new EntityData<>(2, EntityDataTypes.STRING, mobNames.legacy.get(pick)))));
            });
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            UUID self = event.getUser().getUUID();
            Map<Integer, String> known = silenced.get(self);
            Map<Integer, Float> peaks = peakHealth.get(self);
            Map<Integer, Float> maxima = visibleMax.get(self);
            if ((known == null || known.isEmpty()) && (peaks == null || peaks.isEmpty())
                    && (maxima == null || maxima.isEmpty())) return;
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                if (known != null) known.remove(id);
                if (peaks != null) peaks.remove(id);
                if (maxima != null) maxima.remove(id);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            // The client rebuilds its scoreboard and entity table per connection, and reuses entity ids.
            UUID self = event.getUser().getUUID();
            silenced.remove(self);
            peakHealth.remove(self);
            visibleMax.remove(self);
            silencedTeams.remove(self);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT
                || event.getPacketType() == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            // Up to 1.19.4 ServerEntity.sendPairingData handed out the full effect list, amplifier included,
            // of every entity a player starts tracking.
            if (!spoofEffects) return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;

            int entityId = event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT
                    ? new WrapperPlayServerEntityEffect(event).getEntityId()
                    : new WrapperPlayServerRemoveEntityEffect(event).getEntityId();
            if (!ownHealthSafe(receiver, entityId)) return;

            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Server.PLUGIN_MESSAGE) {
            if (!spoofBrand) return;
            WrapperPlayServerPluginMessage wrapper = new WrapperPlayServerPluginMessage(event);
            if (!brandChannel(wrapper.getChannelName())) return;
            wrapper.setData(brandPayload);
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Configuration.Server.PLUGIN_MESSAGE) {
            // Since 1.20.2 the brand arrives before play starts, so the play channel alone would miss it.
            if (!spoofBrand) return;
            WrapperConfigServerPluginMessage wrapper = new WrapperConfigServerPluginMessage(event);
            if (!brandChannel(wrapper.getChannelName())) return;
            wrapper.setData(brandPayload);
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            if (!spoofEquipment && !namePlainEquipment && !stripEnchantments && !stripPotions
                    && !stripDurability && !stripCount && !stripItemAttributes) return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;

            WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
            int entityId = wrapper.getEntityId();
            if (entityId == receiver.entityID) return;

            // WolfArmorLayer paints a crack overlay from the item's damage, so durability only goes for players.
            TrackerData tracked = stripDurability ? receiver.compensatedEntities.serverPositionsMap.get(entityId) : null;
            boolean damage = tracked != null && tracked.getEntityType() == EntityTypes.PLAYER;
            if (!spoofEquipment && !namePlainEquipment && !stripEnchantments && !stripPotions
                    && !damage && !stripCount && !stripItemAttributes) return;

            List<Equipment> equipment = wrapper.getEquipment();
            List<Equipment> rewritten = null;

            for (int i = 0; i < equipment.size(); i++) {
                Equipment slot = equipment.get(i);
                ItemStack stripped = strip(slot.getItem(), spoofEquipment, namePlainEquipment, damage, stripCount, receiver.getClientVersion());
                if (stripped == null) continue;

                if (rewritten == null) rewritten = new ArrayList<>(equipment);
                rewritten.set(i, new Equipment(slot.getSlot(), stripped));
            }

            if (rewritten != null) {
                wrapper.setEquipment(rewritten);
                event.markForReEncode(true);
            }
        }
    }


    // A team with NameTagVisibility NEVER stops vanilla drawing the tag at all, hover included.
    private static boolean silence(GrimPlayer receiver, int entityId, PacketSendEvent event) {
        PacketEntity entity = receiver.compensatedEntities.getEntity(entityId);
        if (entity == null) return false;

        UUID uuid = entity.getUuid();
        if (uuid == null) return false; // no entity uuid below 1.9, nothing to put in a team

        TeamHandler teams = receiver.checkManager.get(TeamHandler.class);
        // Joining our team would drop it out of the one it is in, changing its colour and collision.
        if (teams == null || teams.getEntityTeam(entity) != null) return false;

        String member = uuid.toString();
        if (silenced.computeIfAbsent(receiver.uuid, k -> new ConcurrentHashMap<>()).putIfAbsent(entityId, member) != null) {
            return true;
        }

        event.getPostTasks().add(() -> mute(receiver, member));
        return true;
    }

    private static void mute(GrimPlayer receiver, String member) {
        if (silencedTeams.add(receiver.uuid)) {
            receiver.user.sendPacketSilently(new WrapperPlayServerTeams(SILENT_TEAM,
                    WrapperPlayServerTeams.TeamMode.CREATE,
                    Optional.of(new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                            Component.text(SILENT_TEAM), Component.empty(), Component.empty(),
                            WrapperPlayServerTeams.NameTagVisibility.NEVER,
                            WrapperPlayServerTeams.CollisionRule.ALWAYS,
                            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE)),
                    List.<String>of()));
        }
        receiver.user.sendPacketSilently(new WrapperPlayServerTeams(SILENT_TEAM,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Optional.empty(), List.of(member)));
    }

    // A name the server marks visible has to win, or holograms and tags meant for everyone stay erased.
    private static void unmute(GrimPlayer receiver, int entityId) {
        Map<Integer, String> known = silenced.get(receiver.uuid);
        String member = known == null ? null : known.remove(entityId);
        if (member == null) return;
        receiver.user.sendPacketSilently(new WrapperPlayServerTeams(SILENT_TEAM,
                WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES, Optional.empty(), List.of(member)));
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        silenced.remove(uuid);
        silencedTeams.remove(uuid);
        peakHealth.remove(uuid);
        visibleMax.remove(uuid);
        walks.remove(uuid);
    }

    // The client clamps health down to max_health itself, so anything above the max it was told is erased.
    private static float fakeHealth(@Nullable UUID target, float clientMax) {
        float ceiling = Math.max(1f, Math.min(healthValue, clientMax));
        float span = Math.min(healthSpread, ceiling - 1f);
        if (span <= 0 || target == null) return ceiling;

        long seed = target.getMostSignificantBits() * 31 + target.getLeastSignificantBits() + HEALTH_SALT;
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;

        int steps = (int) (span * 2);
        return steps <= 0 ? ceiling : ceiling - Math.floorMod(seed, steps + 1) / 2f;
    }

    private static final class Walk {
        float shown;
        float real;
        float ceiling;

        Walk(float shown, float real, float ceiling) {
            this.shown = shown;
            this.real = real;
            this.ceiling = ceiling;
        }
    }

    // Keyed by target: the real value is the same in every copy, so the step is taken once.
    private static float walked(UUID target, float real, float ceiling) {
        Walk walk = walks.computeIfAbsent(target, k -> new Walk(ceiling, real, ceiling));
        synchronized (walk) {
            if (walk.ceiling != ceiling) {
                // A walk anchored to an older max would sit at a fraction of the new one forever. Carry the
                // fraction across instead of jumping or re-rolling.
                walk.shown = Math.max(1f, Math.min(ceiling, walk.shown / walk.ceiling * ceiling));
                walk.ceiling = ceiling;
            }
            if (real != walk.real) {
                if (real < walk.real) {
                    // Independent of the real hit, or the amount taken off leaks the damage it stands for.
                    walk.shown -= 0.5f + ThreadLocalRandom.current().nextInt(0, 7) * 0.5f;
                    // The blow that should have finished them lands on someone who heals instead
                    if (walk.shown < 1f) {
                        float healed = ceiling * (0.3f + ThreadLocalRandom.current().nextFloat() * 0.3f);
                        walk.shown = Math.max(1f, Math.round(healed * 2) / 2f);
                    }
                } else {
                    walk.shown = Math.min(ceiling, walk.shown + 1f);
                }
                walk.real = real;
            }
            return walk.shown;
        }
    }

    // Unknown objectives stay hidden until proven to carry something else, so nothing leaks meanwhile.
    private static boolean provenHarmless(String objective, String owner, int value) {
        if (harmlessScores.contains(objective)) return true;
        if (value == Integer.MIN_VALUE) return false;

        PlatformPlayer target = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromName(owner);
        double real = target == null ? -1 : target.getHealth();
        if (real < 0) return false;

        if (value == Math.round(real) || value == Math.ceil(real) || value == Math.floor(real)
                || value == Math.round(real / 2) || value == Math.round(real * 10)) {
            scoreMisses.remove(objective);
            return false;
        }

        if (scoreMisses.merge(objective, 1, Integer::sum) >= 4) {
            harmlessScores.add(objective);
            scoreMisses.remove(objective);
            return true;
        }
        return false;
    }

    private static void rememberMax(GrimPlayer receiver, int entityId, float max) {
        visibleMax.computeIfAbsent(receiver.uuid, k -> new ConcurrentHashMap<>()).put(entityId, max);
    }

    private static float visibleMaxFor(GrimPlayer receiver, int entityId) {
        // sendPairingData sends the metadata before the attributes, so waiting to be told the max is always
        // one packet too late. When we rewrite every max_health property ourselves, that value is the answer.
        if (spoofMaxHealth) return healthValue;
        Map<Integer, Float> known = visibleMax.get(receiver.uuid);
        Float max = known == null ? null : known.get(entityId);
        return max == null ? 20f : max;
    }

    private static @Nullable UUID targetUuid(GrimPlayer receiver, int entityId) {
        PacketEntity entity = receiver.compensatedEntities.getEntity(entityId);
        return entity == null ? null : entity.getUuid();
    }

    // The client draws hearts for what it spectates and for its mount, so those count as its own health.
    private static boolean ownHealthSafe(GrimPlayer receiver, int entityId) {
        if (entityId == receiver.entityID) return false;
        if (!receiver.cameraEntity.isSelf()) return false;
        return receiver.getRidingVehicleId() != entityId;
    }

    @SuppressWarnings("unchecked")
    private static EntityData<?> copyWith(EntityData<?> data, Object value) {
        return new EntityData<>(data.getIndex(), (EntityDataType<Object>) data.getType(), value);
    }

    private static @Nullable Double neutralValue(@Nullable Attribute attribute) {
        if (attribute == null || attribute.getName() == null) return null;
        // Pre-1.16 the key arrives namespaced as generic.movement_speed, and packetevents keeps that form.
        String key = attribute.getName().getKey();
        int dot = key.lastIndexOf('.');
        return NEUTRAL_ATTRIBUTES.get(dot < 0 ? key : key.substring(dot + 1));
    }

    private static boolean isMaxHealth(@Nullable Attribute attribute) {
        if (attribute == null) return false;
        if (attribute == Attributes.MAX_HEALTH || attribute == Attributes.GENERIC_MAX_HEALTH) return true;
        return attribute.getName() != null && attribute.getName().getKey().endsWith("max_health");
    }

    private static @Nullable Object valueAt(List<EntityData<?>> metadata, int index) {
        for (EntityData<?> data : metadata) {
            if (data.getIndex() == index) return data.getValue();
        }
        return null;
    }

    private static int pickIndex(Names names, boolean replace) {
        if (!replace || names.component.isEmpty()) return -1;
        return ThreadLocalRandom.current().nextInt(names.component.size());
    }

    private record Names(List<Component> component, List<String> json, List<String> legacy) {
        static final Names EMPTY = new Names(List.of(), List.of(), List.of());

        static Names of(List<String> raw, boolean parseColors) {
            List<Component> component = new ArrayList<>(raw.size());
            List<String> json = new ArrayList<>(raw.size());
            List<String> legacy = new ArrayList<>(raw.size());
            for (String line : raw) {
                Component parsed = parseColors ? MessageUtil.miniMessage(line) : Component.text(line);
                component.add(parsed);
                json.add(GsonComponentSerializer.gson().serialize(parsed));
                legacy.add(LegacyComponentSerializer.legacySection().serialize(parsed));
            }
            return new Names(List.copyOf(component), List.copyOf(json), List.copyOf(legacy));
        }
    }


    private static ItemEnchantments decoyEnchantments() {
        return new ItemEnchantments(Map.of(DECOY_ENCHANTMENT, 1));
    }

    private static boolean decoyEnchantmentTag(NBTCompound nbt, String key, boolean modern, ClientVersion version) {
        NBTList<NBTCompound> present = nbt.getCompoundListTagOrNull(key);
        if (present == null || present.size() == 0) return false;

        NBTCompound entry = new NBTCompound();
        if (modern) {
            entry.setTag("id", new NBTString(DECOY_ENCHANTMENT.getName().toString()));
        } else {
            entry.setTag("id", new NBTShort((short) DECOY_ENCHANTMENT.getId(version)));
        }
        entry.setTag("lvl", new NBTShort((short) 1));

        NBTList<NBTCompound> replacement = NBTList.createCompoundList();
        replacement.addTag(entry);
        nbt.setTag(key, replacement);
        return true;
    }

    private static byte[] brandPayload(String brand) {
        byte[] utf = brand.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = new byte[5];
        int size = 0;
        int length = utf.length;
        while ((length & ~0x7F) != 0) {
            prefix[size++] = (byte) ((length & 0x7F) | 0x80);
            length >>>= 7;
        }
        prefix[size++] = (byte) length;

        byte[] payload = new byte[size + utf.length];
        System.arraycopy(prefix, 0, payload, 0, size);
        System.arraycopy(utf, 0, payload, size, utf.length);
        return payload;
    }

    private static boolean brandChannel(String channel) {
        return "minecraft:brand".equals(channel) || "MC|Brand".equals(channel);
    }

    private static boolean itemStrippingOn() {
        return spoofGroundItems || namePlainItems || stripEnchantments || stripPotions || stripItemAttributes;
    }

    private static @Nullable ItemStack strip(@Nullable ItemStack item, boolean names, boolean plain, boolean damage, boolean count, ClientVersion version) {
        if (item == null || item.isEmpty()) return null;
        boolean legacyJson = version.isNewerThanOrEquals(ClientVersion.V_1_13);

        ItemStack copy = item.copy();
        boolean changed = false;

        int pick = names || plain ? pickIndex(itemNames, replaceItemNames) : -1;
        boolean modern = version.isNewerThanOrEquals(ClientVersion.V_1_20_5);
        if (names && copy.hasComponent(ComponentTypes.CUSTOM_NAME)) {
            if (pick < 0) copy.unsetComponent(ComponentTypes.CUSTOM_NAME);
            else copy.setComponent(ComponentTypes.CUSTOM_NAME, itemNames.component.get(pick));
            changed = true;
        } else if (plain && pick >= 0 && modern && !copy.hasComponent(ComponentTypes.CUSTOM_NAME)) {
            // The vanilla name never travels in the packet, so denying it means giving the item one.
            // ItemEntity is not isPickable(), so nothing ever draws it.
            copy.setComponent(ComponentTypes.CUSTOM_NAME, itemNames.component.get(pick));
            changed = true;
        }

        if (plain && pick >= 0 && !modern) {
            NBTCompound tag = copy.getOrCreateTag();
            NBTCompound display = tag.getCompoundTagOrNull("display");
            if (display == null) {
                display = new NBTCompound();
                tag.setTag("display", display);
            }
            if (display.getTagOrNull("Name") == null) {
                display.setTag("Name", new NBTString(legacyJson ? itemNames.json.get(pick) : itemNames.legacy.get(pick)));
                changed = true;
            }
        }
        if (names && copy.hasComponent(ComponentTypes.ITEM_NAME)) {
            copy.unsetComponent(ComponentTypes.ITEM_NAME);
            changed = true;
        }
        if (names && copy.hasComponent(ComponentTypes.LORE)) {
            copy.unsetComponent(ComponentTypes.LORE);
            changed = true;
        }
        if (stripPotions) {
            if (copy.hasComponent(ComponentTypes.POTION_CONTENTS)) {
                copy.unsetComponent(ComponentTypes.POTION_CONTENTS);
                changed = true;
            }
        }
        // Unset falls back to the item prototype, so this hides what a plugin added, not the item's own.
        if (stripItemAttributes && copy.hasComponent(ComponentTypes.ATTRIBUTE_MODIFIERS)) {
            copy.unsetComponent(ComponentTypes.ATTRIBUTE_MODIFIERS);
            changed = true;
        }
        if (stripEnchantments) {
            // A decoy rather than removal: the glint comes from hasFoil(), any enchantment at all.
            if (!copy.getComponentOr(ComponentTypes.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
                copy.setComponent(ComponentTypes.ENCHANTMENTS, decoyEnchantments());
                changed = true;
            }
            if (!copy.getComponentOr(ComponentTypes.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
                copy.setComponent(ComponentTypes.STORED_ENCHANTMENTS, decoyEnchantments());
                changed = true;
            }
        }

        // Before 1.13 durability shares the data value with the block variant, hence isDamageableItem.
        if (damage && copy.isDamageableItem() && copy.getDamageValue() != 0) {
            copy.setDamageValue(0);
            changed = true;
        }

        if (count && copy.getAmount() != 1) {
            copy.setAmount(1);
            changed = true;
        }

        NBTCompound nbt = copy.getNBT();
        if (nbt != null) {
            // display also carries color (leather dye) and MapColor, so only the two text tags go.
            NBTCompound display = names ? nbt.getCompoundTagOrNull("display") : null;
            if (display != null) {
                if (display.removeTag("Name") != null) changed = true;
                if (pick >= 0) display.setTag("Name", new NBTString(legacyJson ? itemNames.json.get(pick) : itemNames.legacy.get(pick)));
                if (display.removeTag("Lore") != null) changed = true;
                if (display.isEmpty()) nbt.removeTag("display");
            }
            if (stripPotions) {
                if (nbt.removeTag("Potion") != null) changed = true;
                if (nbt.removeTag("CustomPotionEffects") != null) changed = true;
                if (nbt.removeTag("custom_potion_effects") != null) changed = true;
                if (nbt.removeTag("CustomPotionColor") != null) changed = true;
            }
            if (stripItemAttributes && nbt.removeTag("AttributeModifiers") != null) changed = true;
            if (stripEnchantments) {
                changed |= decoyEnchantmentTag(nbt, "ench", false, version);
                changed |= decoyEnchantmentTag(nbt, "Enchantments", true, version);
                changed |= decoyEnchantmentTag(nbt, "StoredEnchantments", true, version);
            }
        }

        return changed ? copy : null;
    }
}
