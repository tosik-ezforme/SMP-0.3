package ru.smp.legendary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Меню администратора. Открывается предметом «Звезда управления» (ПКМ) или командой /legendary menu.
 * Разделы: оружие, предметы, прогресс убийств, сердца, алтари, инструменты. Цель действий (кому выдавать,
 * чей прогресс менять) выбирается кнопкой «Цель» (по умолчанию — сам администратор).
 */
public final class AdminMenu implements Listener {

    public static final String ID = "admin_menu";
    private static final String PERM = "legendary.command.menu";

    private enum Page { MAIN, WEAPONS, ITEMS, PROGRESS, HEARTS, ALTARS, TOOLS, PLAYERS, COPIES }

    private static final class Holder implements InventoryHolder {
        final Page page;
        Inventory inv;

        Holder(Page page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final LegendaryPlugin plugin;
    /** Выбранная цель действий для каждого администратора. */
    private final Map<UUID, UUID> targets = new HashMap<>();
    private String pendingDelete;
    private long pendingAt;

    public AdminMenu(LegendaryPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ предмет

    public boolean is(ItemStack s) {
        return ("f:" + ID).equals(plugin.items().typeId(s));
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Util.color("&6&lЗвезда управления"));
        m.setLore(List.of(Util.color("&7ПКМ — открыть меню администратора:"),
                Util.color("&7оружие, предметы, прогресс, сердца, алтари."),
                Util.color("&8Только для администраторов")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(plugin.keyId, PersistentDataType.STRING, "f:" + ID);
        plugin.items().applyModel(m, ID);
        it.setItemMeta(m);
        return it;
    }

    private boolean allowed(Player p) {
        return p.hasPermission(PERM) || p.hasPermission("legendary.admin");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !is(e.getItem())) {
            return;
        }
        e.setCancelled(true);
        if (!allowed(e.getPlayer())) {
            e.getPlayer().sendMessage(Util.color("&cЭтот предмет работает только у администраторов."));
            return;
        }
        open(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        targets.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ построение страниц

    private static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Util.color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Util.color(s));
        }
        meta.setLore(l);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private Player targetOf(Player admin) {
        UUID t = targets.get(admin.getUniqueId());
        Player p = t == null ? null : Bukkit.getPlayer(t);
        return p == null ? admin : p;
    }

    private ItemStack targetIcon(Player admin) {
        Player t = targetOf(admin);
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwningPlayer(t);
        sm.setDisplayName(Util.color("&eЦель: &f" + t.getName()));
        sm.setLore(List.of(Util.color("&7Кому выдаются предметы и чей"), Util.color("&7прогресс/сердца меняются."),
                Util.color("&aНажмите, чтобы выбрать другого игрока")));
        it.setItemMeta(sm);
        return it;
    }

    private Holder holder(Page page, int size, String title) {
        Holder h = new Holder(page);
        h.inv = Bukkit.createInventory(h, size, Util.color(title));
        return h;
    }

    private void fillFrame(Inventory inv) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    public void open(Player p) {
        show(p, Page.MAIN);
    }

    private void show(Player p, Page page) {
        Holder h;
        switch (page) {
            case MAIN -> {
                h = holder(page, 27, "&6&lLegendaryWeapons — управление");
                fillFrame(h.inv);
                h.inv.setItem(10, icon(Material.NETHERITE_SWORD, "&6Легендарное оружие", "&7Выдать любое из 12 видов"));
                h.inv.setItem(11, icon(Material.CHEST, "&bПредметы", "&7Броня, ведро еды, компоненты"));
                h.inv.setItem(12, icon(Material.EXPERIENCE_BOTTLE, "&aПрогресс убийств", "&7Установить прогресс оружия цели"));
                h.inv.setItem(13, icon(Material.RED_DYE, "&cСердца", "&7Изменить количество сердец цели"));
                h.inv.setItem(14, icon(Material.LODESTONE, "&dАлтари", "&7Построить, расставить, телепорт"));
                h.inv.setItem(15, icon(Material.COMMAND_BLOCK, "&eИнструменты", "&7Перезагрузка, сброс перезарядок"));
                h.inv.setItem(16, targetIcon(p));
                h.inv.setItem(19, icon(Material.BARRIER, "&4Экземпляры на карте", "&7Лимит копий каждого оружия", "&7и удаление с карты"));
            }
            case COPIES -> {
                h = holder(page, 27, "&4Лимит: ЛКМ +1, ПКМ -1, Shift+ПКМ x2 — удалить");
                fillFrame(h.inv);
                int i = 9;
                for (Weapon w : plugin.items().weapons().values()) {
                    h.inv.setItem(i++, icon(w.material, w.name,
                            "&7На карте сейчас: &f" + plugin.rituals().copies(w.id),
                            "&7Лимит (макс. на карте): &f" + plugin.rituals().limit(w.id),
                            "", "&aЛКМ — лимит +1", "&cПКМ — лимит -1",
                            "&4Shift+ПКМ дважды — удалить ВСЕ копии с карты"));
                }
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            case WEAPONS -> {
                h = holder(page, 27, "&6Оружие: ЛКМ — выдать цели");
                fillFrame(h.inv);
                int i = 0;
                for (Weapon w : plugin.items().weapons().values()) {
                    ItemStack it = plugin.items().createWeapon(w.id);
                    ItemMeta m = it.getItemMeta();
                    List<String> lore = new ArrayList<>(m.getLore() == null ? List.of() : m.getLore());
                    lore.add("");
                    lore.add(Util.color("&aЛКМ — выдать: &f" + targetOf(p).getName()));
                    lore.add(Util.color(w.unique() ? "&8Уникальное: в мире может быть только одно" : "&8Не уникальное"));
                    m.setLore(lore);
                    // у иконки убираем метку предмета, чтобы её нельзя было «вытащить» как настоящую
                    m.getPersistentDataContainer().remove(plugin.keyId);
                    m.getPersistentDataContainer().remove(plugin.keyInstance);
                    it.setItemMeta(m);
                    h.inv.setItem(i < 9 ? i + 9 : i + 9 + 0, it); // два ряда: 9..17, 18..20
                    i++;
                }
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            case ITEMS -> {
                h = holder(page, 27, "&bПредметы: ЛКМ — выдать цели");
                fillFrame(h.inv);
                h.inv.setItem(10, icon(Material.COPPER_CHESTPLATE, "&6Медный комплект", "&7Все 4 части"));
                h.inv.setItem(11, icon(Material.COPPER_HELMET, "&6Шлем «Искатель»"));
                h.inv.setItem(12, icon(Material.COPPER_CHESTPLATE, "&6Нагрудник «Проводник»"));
                h.inv.setItem(13, icon(Material.COPPER_LEGGINGS, "&6Поножи «Гальваника»"));
                h.inv.setItem(14, icon(Material.COPPER_BOOTS, "&6Ботинки «Импульс»"));
                h.inv.setItem(15, icon(Material.MILK_BUCKET, "&dВедро еды", "&7Бесконечное, перезарядка 30 с"));
                h.inv.setItem(16, icon(Material.HEAVY_CORE, "&6Навершие булавы", "&7Компонент"));
                h.inv.setItem(19, icon(Material.IRON_SWORD, "&7Основа кинжала", "&7Компонент"));
                h.inv.setItem(20, icon(Material.NETHER_STAR, "&6Звезда управления", "&7Это меню"));
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            case PROGRESS -> {
                h = holder(page, 27, "&aПрогресс: ЛКМ открыть, ПКМ 0, Shift +1");
                fillFrame(h.inv);
                Player t = targetOf(p);
                int need = plugin.progress().need();
                int i = 9;
                for (Weapon w : plugin.items().weapons().values()) {
                    int c = plugin.progress().count(t.getUniqueId(), w.id);
                    h.inv.setItem(i++, icon(w.material, w.name, "&7Игрок: &f" + t.getName(),
                            "&7Прогресс: &f" + Math.min(c, need) + "/" + need + (c >= need ? " &a(открыта)" : ""),
                            "", "&aЛКМ — открыть вторую способность (" + need + ")",
                            "&cПКМ — сбросить в 0", "&eShift+ЛКМ — +1"));
                }
                h.inv.setItem(4, targetIcon(p));
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            case HEARTS -> {
                h = holder(page, 27, "&cСердца");
                fillFrame(h.inv);
                Player t = targetOf(p);
                int cur = plugin.hearts().get(t.getUniqueId());
                h.inv.setItem(4, targetIcon(p));
                h.inv.setItem(11, icon(Material.RED_CONCRETE, "&c-1 сердце", "&7Сейчас: &f" + cur));
                h.inv.setItem(13, icon(Material.RED_DYE, "&fСейчас: " + cur, "&7Стандарт: " + plugin.hearts().defaultHearts(),
                        "&aНажмите — сбросить до стандарта"));
                h.inv.setItem(15, icon(Material.LIME_CONCRETE, "&a+1 сердце", "&7Сейчас: &f" + cur));
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            case ALTARS -> {
                h = holder(page, 54, "&dАлтари: ЛКМ — построить под собой, ПКМ — случайно");
                fillFrame(h.inv);
                int i = 0;
                for (Weapon w : plugin.items().weapons().values()) {
                    boolean exists = plugin.altars().all().stream().anyMatch(a -> a.type.equals(w.id));
                    h.inv.setItem(i++, icon(w.material, w.altarName, "&7Тип: &f" + w.id,
                            exists ? "&eАлтарь этого типа уже есть" : "&7Алтаря пока нет",
                            "&aЛКМ — построить на вашем месте", "&bПКМ — случайное место на карте"));
                }
                h.inv.setItem(45, icon(Material.ENDER_EYE, "&bРасставить ВСЕ случайно", "&7Только типы, у которых ещё нет алтаря"));
                h.inv.setItem(49, icon(Material.ARROW, "&7Назад"));
                int slot = 18;
                for (AltarManager.Altar a : plugin.altars().all()) {
                    if (slot > 44) {
                        break;
                    }
                    h.inv.setItem(slot++, icon(Material.LODESTONE, "&e#" + a.id + " &f" + a.type,
                            "&7" + a.world + " " + a.x + " " + a.y + " " + a.z, "&aЛКМ — телепорт"));
                }
            }
            case TOOLS -> {
                h = holder(page, 27, "&eИнструменты");
                fillFrame(h.inv);
                h.inv.setItem(11, icon(Material.REPEATER, "&aПерезагрузить config.yml"));
                h.inv.setItem(13, icon(Material.CLOCK, "&bСбросить перезарядки цели", "&7Цель: &f" + targetOf(p).getName()));
                h.inv.setItem(15, icon(Material.BOOK, "&7Журнал", "&7Смотреть: /legendary banlog"));
                h.inv.setItem(22, icon(Material.ARROW, "&7Назад"));
            }
            default -> {
                h = holder(Page.PLAYERS, 54, "&eВыбор цели");
                fillFrame(h.inv);
                int i = 0;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (i > 44) {
                        break;
                    }
                    ItemStack it = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sm = (SkullMeta) it.getItemMeta();
                    sm.setOwningPlayer(o);
                    sm.setDisplayName(Util.color("&f" + o.getName()));
                    sm.setLore(List.of(Util.color("&aНажмите, чтобы выбрать целью")));
                    it.setItemMeta(sm);
                    h.inv.setItem(i++, it);
                }
                h.inv.setItem(49, icon(Material.ARROW, "&7Назад"));
            }
        }
        p.openInventory(h.inv);
    }

    // ------------------------------------------------------------ клики

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        if (!allowed(p)) {
            p.closeInventory();
            return;
        }
        int slot = e.getSlot();
        boolean left = e.isLeftClick();
        boolean right = e.isRightClick();
        boolean shift = e.isShiftClick();
        List<Weapon> ws = new ArrayList<>(plugin.items().weapons().values());
        Player t = targetOf(p);

        switch (h.page) {
            case MAIN -> {
                switch (slot) {
                    case 10 -> show(p, Page.WEAPONS);
                    case 11 -> show(p, Page.ITEMS);
                    case 12 -> show(p, Page.PROGRESS);
                    case 13 -> show(p, Page.HEARTS);
                    case 14 -> show(p, Page.ALTARS);
                    case 15 -> show(p, Page.TOOLS);
                    case 16 -> show(p, Page.PLAYERS);
                    case 19 -> show(p, Page.COPIES);
                    default -> {
                    }
                }
            }
            case COPIES -> {
                if (slot == 22) {
                    show(p, Page.MAIN);
                } else if (slot >= 9 && slot < 9 + ws.size()) {
                    Weapon w = ws.get(slot - 9);
                    if (shift && right) {
                        long now = System.currentTimeMillis();
                        String want = p.getUniqueId() + ":" + w.id;
                        if (want.equals(pendingDelete) && now - pendingAt < 6000) {
                            pendingDelete = null;
                            int n = plugin.rituals().removeFromMap(w.id, p.getName());
                            p.sendMessage(Util.color("&aУдалено с карты: &f" + w.plainName() + " &a(экземпляров: " + n + ")"));
                        } else {
                            pendingDelete = want;
                            pendingAt = now;
                            p.sendMessage(Util.color("&cПовторите Shift+ПКМ в течение 6 с, чтобы удалить все копии &f" + w.plainName()));
                        }
                    } else if (right) {
                        plugin.rituals().setLimit(w.id, plugin.rituals().limit(w.id) - 1);
                        plugin.adminLog(p.getName(), "limit_set", w.id + " = " + plugin.rituals().limit(w.id) + " (menu)");
                    } else if (left) {
                        plugin.rituals().setLimit(w.id, plugin.rituals().limit(w.id) + 1);
                        plugin.adminLog(p.getName(), "limit_set", w.id + " = " + plugin.rituals().limit(w.id) + " (menu)");
                    }
                    show(p, Page.COPIES);
                }
            }
            case WEAPONS -> {
                if (slot == 22) {
                    show(p, Page.MAIN);
                } else if (slot >= 9 && slot < 9 + ws.size()) {
                    giveWeapon(p, t, ws.get(slot - 9));
                }
            }
            case ITEMS -> {
                switch (slot) {
                    case 10 -> {
                        for (String id : ItemRegistry.ARMOR_IDS) {
                            ItemStack a = plugin.items().createArmor(id);
                            if (a != null) {
                                RitualManager.give(t, a);
                            }
                        }
                        done(p, t, "медный комплект");
                    }
                    case 11, 12, 13, 14 -> {
                        ItemStack a = plugin.items().createArmor(ItemRegistry.ARMOR_IDS.get(slot - 11));
                        if (a != null) {
                            RitualManager.give(t, a);
                            done(p, t, a.getItemMeta().getDisplayName());
                        }
                    }
                    case 15 -> {
                        RitualManager.give(t, plugin.food().create());
                        done(p, t, "Ведро еды");
                    }
                    case 16 -> {
                        RitualManager.give(t, plugin.items().createComponent("mace_head", 1));
                        done(p, t, "Навершие булавы");
                    }
                    case 19 -> {
                        RitualManager.give(t, plugin.items().createComponent("dagger_base", 1));
                        done(p, t, "Основа кинжала");
                    }
                    case 20 -> {
                        RitualManager.give(t, create());
                        done(p, t, "Звезда управления");
                    }
                    case 22 -> show(p, Page.MAIN);
                    default -> {
                    }
                }
            }
            case PROGRESS -> {
                if (slot == 22) {
                    show(p, Page.MAIN);
                } else if (slot == 4) {
                    show(p, Page.PLAYERS);
                } else if (slot >= 9 && slot < 9 + ws.size()) {
                    Weapon w = ws.get(slot - 9);
                    int cur = plugin.progress().count(t.getUniqueId(), w.id);
                    int next = shift ? cur + 1 : right ? 0 : left ? plugin.progress().need() : cur;
                    plugin.progress().set(t.getUniqueId(), w.id, next);
                    plugin.adminLog(p.getName(), "progress_set", t.getName() + " " + w.id + " = " + next + " (menu)");
                    show(p, Page.PROGRESS);
                }
            }
            case HEARTS -> {
                int cur = plugin.hearts().get(t.getUniqueId());
                int next = cur;
                switch (slot) {
                    case 11 -> next = cur - 1;
                    case 15 -> next = cur + 1;
                    case 13 -> next = plugin.hearts().defaultHearts();
                    case 4 -> {
                        show(p, Page.PLAYERS);
                        return;
                    }
                    case 22 -> {
                        show(p, Page.MAIN);
                        return;
                    }
                    default -> {
                        return;
                    }
                }
                next = Math.max(plugin.hearts().min(), Math.min(plugin.hearts().maxFor(), next));
                plugin.hearts().set(t.getUniqueId(), next);
                plugin.adminLog(p.getName(), "hearts_set", t.getName() + " = " + next + " (menu)");
                show(p, Page.HEARTS);
            }
            case ALTARS -> altarsClick(p, slot, left, right, ws);
            case TOOLS -> {
                switch (slot) {
                    case 11 -> {
                        plugin.reloadAll();
                        p.sendMessage(Util.color("&aconfig.yml перезагружен."));
                    }
                    case 13 -> {
                        plugin.cooldowns().clearPlayer(t.getUniqueId());
                        p.sendMessage(Util.color("&aПерезарядки игрока &f" + t.getName() + " &aсброшены."));
                    }
                    case 22 -> show(p, Page.MAIN);
                    default -> {
                    }
                }
            }
            case PLAYERS -> {
                if (slot == 49) {
                    show(p, Page.MAIN);
                } else if (e.getCurrentItem() != null && e.getCurrentItem().getItemMeta() instanceof SkullMeta sm
                        && sm.getOwningPlayer() != null) {
                    OfflinePlayer op = sm.getOwningPlayer();
                    targets.put(p.getUniqueId(), op.getUniqueId());
                    show(p, Page.MAIN);
                }
            }
        }
    }

    private void altarsClick(Player p, int slot, boolean left, boolean right, List<Weapon> ws) {
        if (slot == 49) {
            show(p, Page.MAIN);
            return;
        }
        if (slot == 45) {
            List<String> types = new ArrayList<>(plugin.items().weapons().keySet());
            types.removeIf(tp -> plugin.altars().all().stream().anyMatch(a -> a.type.equals(tp)));
            p.closeInventory();
            if (types.isEmpty()) {
                p.sendMessage(Util.color("&eАлтари всех типов уже есть."));
            } else {
                plugin.spawner().random(p, types, p.getWorld());
            }
            return;
        }
        if (slot >= 0 && slot < ws.size()) {
            Weapon w = ws.get(slot);
            p.closeInventory();
            if (right) {
                plugin.spawner().random(p, new ArrayList<>(List.of(w.id)), p.getWorld());
            } else if (left) {
                AltarManager.Altar a = plugin.spawner().spawnAt(p, w.id, p.getWorld(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
                p.sendMessage(Util.color(a == null ? "&cНе удалось построить алтарь." : "&aАлтарь &f#" + a.id + " &aпостроен."));
            }
            return;
        }
        if (slot >= 18 && slot <= 44) {
            int idx = slot - 18;
            List<AltarManager.Altar> list = new ArrayList<>(plugin.altars().all());
            if (idx < list.size() && list.get(idx).location() != null) {
                AltarManager.Altar a = list.get(idx);
                p.closeInventory();
                p.teleport(a.location().add(0.5, 3, 0.5 + AltarBuilder.radius(plugin) - 2));
                p.sendMessage(Util.color("&aТелепорт к алтарю #" + a.id + " (" + a.type + ")."));
            }
        }
    }

    private void done(Player admin, Player target, String what) {
        plugin.adminLog(admin.getName(), "give", what + " -> " + target.getName() + " (menu)");
        admin.sendMessage(Util.color("&aВыдано: &f" + what + " &aигроку &f" + target.getName()));
    }

    /** Выдача оружия администратором: правило «одно на карте» админ обходит осознанно (запись в журнал). */
    private void giveWeapon(Player admin, Player target, Weapon w) {
        ItemStack it = plugin.items().createWeapon(w.id);
        plugin.registerInstance(w.id, plugin.items().instanceId(it));
        RitualManager.give(target, it);
        done(admin, target, w.plainName());
    }
}
