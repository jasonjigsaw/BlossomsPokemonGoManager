package me.corriekay.pokegoutil.utils.pokemon;

import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonMeta;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.api.pokemon.PokemonMoveMeta;
import com.pokegoapi.api.pokemon.PokemonMoveMetaRegistry;

import me.corriekay.pokegoutil.utils.ConfigKey;
import me.corriekay.pokegoutil.utils.ConfigNew;
import me.corriekay.pokegoutil.utils.Utilities;

import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Enums.PokemonMoveOuterClass.PokemonMove;

/**
 * A Utility class providing several methods to calculate stats and values of Pokémon.
 * Most important ivRating, Duel Ability, Gym Offense and Defense.
 */
public final class PokemonCalculationUtils {
    public static final double NORMAL_MULTIPLIER = 1.0;
    public static final double STAB_MULTIPLIER = 1.25;

    /**
     * Damage bonus from a critical hit - currently no damage bonus in game, change when game is fixed.
     */
    public static final double CRIT_DAMAGE_BONUS = 0;

    public static final int MOVE2_CHARGE_DELAY_MS = 500;
    public static final int MILLISECONDS_FACTOR = 1000;
    public static final int WEAVE_NUMBER = 100000;
    public static final int MOVE_2_ADDITIONAL_DELAY = 2000;
    public static final int WEAVE_LENGTH_SECONDS = 100;
    public static final int MAX_MOVE_ENERGY = 100;

    /** Prevent initializing this class. */
    private PokemonCalculationUtils() {
    }

    /**
     * Rates the IV of given Pokémon.
     * If the setting for Alternative IV Calculation is chosen, it uses the advanced calculation, otherwise simple
     *
     * @param p The Pokémon to rate.
     * @return IV Rating.
     */
    public static double ivRating(final Pokemon p) {
        if (ConfigNew.getConfig().getBool(ConfigKey.ALTERNATIVE_IV_CALCULATION)) {
            final PokemonMeta meta = p.getMeta();
            final double cpMax = (meta.getBaseAttack() + PokemonUtils.MAX_IV)
                * Math.pow(meta.getBaseDefense() + PokemonUtils.MAX_IV, 0.5)
                * Math.pow(meta.getBaseStamina() + PokemonUtils.MAX_IV, 0.5);
            final double cpMin = meta.getBaseAttack()
                * Math.pow(meta.getBaseDefense(), 0.5)
                * Math.pow(meta.getBaseStamina(), 0.5);
            final double cpIv = (meta.getBaseAttack() + p.getIndividualAttack())
                * Math.pow(meta.getBaseDefense() + p.getIndividualDefense(), 0.5)
                * Math.pow(meta.getBaseStamina() + p.getIndividualStamina(), 0.5);
            return (cpIv - cpMin) / (cpMax - cpMin);
        } else {
            return Utilities.percentage(p.getIndividualAttack() + p.getIndividualDefense() + p.getIndividualStamina(),
                PokemonUtils.MAX_IV + PokemonUtils.MAX_IV + PokemonUtils.MAX_IV);
        }
    }

    /**
     * Calculates the no weave dps for current move. Just plain damage, without dodging or any other attack.
     *
     * @param p       A Pokemon object.
     * @param primary If it should be calculated for the primary more or the secondary.
     * @return The clean dps.
     */
    public static double dpsForMove(final Pokemon p, final boolean primary) {
        final PokemonMove move = primary ? p.getMove1() : p.getMove2();
        return dpsForMove(p.getPokemonId(), move, primary);
    }

    /**
     * Calculates the no weave dps for current move. Just plain damage, without dodging or any other attack.
     *
     * @param pokemonId The pokemonId to check for.
     * @param move      The move to calculate the dps for.
     * @param primary   If it should be calculated for the primary more or the secondary.
     * @return The clean dps.
     */
    private static double dpsForMove(final PokemonId pokemonId, final PokemonMove move, final boolean primary) {
        final PokemonMoveMeta moveMeta = PokemonMoveMetaRegistry.getMeta(move);
        final int moveDelay = primary ? 0 : MOVE2_CHARGE_DELAY_MS;
        double dps = (double) moveMeta.getPower() / (double) (moveMeta.getTime() + moveDelay) * MILLISECONDS_FACTOR;
        if (PokemonUtils.hasStab(pokemonId, moveMeta.getMove())) {
            dps = dps * STAB_MULTIPLIER;
        }
        return dps;
    }

    /**
     * Duel Ability is Tankiness * Gym Offense. A reasonable measure if you don't often/ever dodge,
     * as then you can only attack for as long as you  can stay positive on HP.
     *
     * @param p A Pokemon object
     * @return Rating of a Pokemon's overall attacking power considering damage, health & defense
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long duelAbility(final Pokemon p) {
        return duelAbility(p.getPokemonId(), p.getMove1(), p.getMove2(), p.getIndividualAttack(), p.getIndividualDefense(), p.getIndividualStamina());
    }

    /**
     * Duel Ability is Tankiness * Gym Offense. A reasonable measure if you don't often/ever dodge,
     * as then you can only attack for as long as you  can stay positive on HP.
     *
     * @param pokemonId The pokemonId of the pokemon
     * @param move1     The first move of the pokemon
     * @param move2     The second move of the pokemon
     * @param attackIV  The attackIV of the pokemon
     * @param defenseIV The defenseIV of the pokemon
     * @param staminaIV The staminaIV of the pokemon
     * @return Rating of a Pokemon's overall attacking power considering damage, health & defense
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long duelAbility(final PokemonId pokemonId,
                                   final PokemonMove move1, final PokemonMove move2,
                                   final int attackIV, final int defenseIV, final int staminaIV) {
        final double duelAbility = PokemonCalculationUtils.gymOffense(pokemonId, move1, move2, attackIV)
            * PokemonCalculationUtils.tankiness(pokemonId, defenseIV, staminaIV);
        return Math.round(duelAbility);
    }

    /**
     * Gym Offense takes the better of No Weave/Weave Damage over 100s and multiplies by the
     * Pokemon's base attack to arrive at a ranking of raw damage output.
     *
     * @param p A Pokemon object
     * @return Rating of a Pokemon's pure offensive ability over time considering move set
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static double gymOffense(final Pokemon p) {
        return gymOffense(p.getPokemonId(), p.getMove1(), p.getMove2(), p.getIndividualAttack());
    }

    /**
     * Gym Offense takes the better of No Weave/Weave Damage over 100s and multiplies by the
     * Pokemon's base attack to arrive at a ranking of raw damage output.
     *
     * @param pokemonId The pokemonId of the pokemon
     * @param move1     The first move of the pokemon
     * @param move2     The second move of the pokemon
     * @param attackIV  The attackIV of the pokemon
     * @return Rating of a Pokemon's pure offensive ability over time considering move set
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static double gymOffense(final PokemonId pokemonId, final PokemonMove move1, final PokemonMove move2, final int attackIV) {
        final PokemonMeta meta = PokemonMetaRegistry.getMeta(pokemonId);
        return Math.max(
            PokemonCalculationUtils.dpsForMove(pokemonId, move1, true) * WEAVE_LENGTH_SECONDS,
            PokemonCalculationUtils.weaveDps(pokemonId, move1, move2, 0)
        ) * (meta.getBaseAttack() + attackIV);
    }

    /**
     * Gym Defense takes the calculated Gym Weave Damage over 100s and multiplies by Tankiness
     * to arrive at a ranking of how much damage a Pokemon will output when defending a gym.
     *
     * @param p A Pokemon object
     * @return Rating of a Pokemon's AI controlled gym defense over time considering move set
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long gymDefense(final Pokemon p) {
        return gymDefense(p.getPokemonId(), p.getMove1(), p.getMove2(), p.getIndividualAttack(), p.getIndividualDefense(), p.getIndividualStamina());
    }

    /**
     * Gym Defense takes the calculated Gym Weave Damage over 100s and multiplies by Tankiness
     * to arrive at a ranking of how much damage a Pokemon will output when defending a gym.
     *
     * @param pokemonId The pokemonId of the pokemon
     * @param move1     The first move of the pokemon
     * @param move2     The second move of the pokemon
     * @param attackIV  The attackIV of the pokemon
     * @param defenseIV The defenseIV of the pokemon
     * @param staminaIV The staminaIV of the pokemon
     * @return Rating of a Pokemon's AI controlled gym defense over time considering move set
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long gymDefense(final PokemonId pokemonId,
                                  final PokemonMove move1, final PokemonMove move2,
                                  final int attackIV, final int defenseIV, final int staminaIV) {
        final PokemonMeta meta = PokemonMetaRegistry.getMeta(pokemonId);
        final double gymDefense = PokemonCalculationUtils.weaveDps(pokemonId, move1, move2, MOVE_2_ADDITIONAL_DELAY)
            * (meta.getBaseAttack() + attackIV)
            * PokemonCalculationUtils.tankiness(pokemonId, defenseIV, staminaIV);
        return Math.round(gymDefense);
    }

    /**
     * Tankiness is basically Base HP * Base Def. An approximation of a Pokemon's relative ability
     * to soak damage compared to other species.
     * <p>
     * Used for duel ability & gym defense calculations
     *
     * @param p A Pokemon object
     * @return Rating of a Pokemon's tankiness :)
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long tankiness(final Pokemon p) {
        return tankiness(p.getPokemonId(), p.getIndividualDefense(), p.getIndividualStamina());
    }

    /**
     * Tankiness is basically Base HP * Base Def. An approximation of a Pokemon's relative ability
     * to soak damage compared to other species.
     * <p>
     * Used for duel ability & gym defense calculations
     *
     * @param pokemonId The pokemonId of the pokemon
     * @param defenseIV The defenseIV of the pokemon
     * @param staminaIV The staminaIV of the pokemon
     * @return Rating of a Pokemon's tankiness :)
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static long tankiness(final PokemonId pokemonId, final int defenseIV, final int staminaIV) {
        final PokemonMeta meta = PokemonMetaRegistry.getMeta(pokemonId);
        return (meta.getBaseStamina() + staminaIV) * (meta.getBaseDefense() + defenseIV);
    }

    /**
     * Weave Damage/100s is determined by figuring out the total Power achieved over 100 seconds
     * by using basic attack enough to charge up enough energy to do a charge attack, and then
     * using charge attack as soon as possible to not waste energy. It is highlighted in green if doing
     * this is the best way to output damage for a moveset.
     *
     * @param p               The pokemon
     * @param additionalDelay Allow a delay in milliseconds for gym offense (0ms) vs gym defense (2000ms)
     * @return Damage over 100 seconds for a Pokemon's moveset
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static double weaveDps(final Pokemon p, final int additionalDelay) {
        return weaveDps(p.getPokemonId(), p.getMove1(), p.getMove2(), additionalDelay);
    }

    /**
     * Weave Damage/100s is determined by figuring out the total Power achieved over 100 seconds
     * by using basic attack enough to charge up enough energy to do a charge attack, and then
     * using charge attack as soon as possible to not waste energy. It is highlighted in green if doing
     * this is the best way to output damage for a moveset.
     *
     * @param pokemonId       The pokemonId of the pokemon
     * @param move1           The first move of the pokemon
     * @param move2           The second move of the pokemon
     * @param additionalDelay Allow a delay in milliseconds for gym offense (0ms) vs gym defense (2000ms)
     * @return Damage over 100 seconds for a Pokemon's moveset
     * @link https://www.reddit.com/r/TheSilphRoad/comments/4vcobt/posthotfix_pokemon_go_full_moveset_rankings/
     * @link i607ch00
     */
    public static double weaveDps(final PokemonId pokemonId, final PokemonMove move1, final PokemonMove move2, final int additionalDelay) {
        final PokemonMoveMeta pm1 = PokemonMoveMetaRegistry.getMeta(move1);
        final PokemonMoveMeta pm2 = PokemonMoveMetaRegistry.getMeta(move2);
        final double moveOneStab = PokemonUtils.hasStab(pokemonId, move1) ? STAB_MULTIPLIER : NORMAL_MULTIPLIER;
        final double moveTwoStab = PokemonUtils.hasStab(pokemonId, move2) ? STAB_MULTIPLIER : NORMAL_MULTIPLIER;

        //Translation reference
        //R = Move 1 Power
        //S = Move 1 Stab
        //T = Move 1 Speed
        //U = Move 1 Energy
        //X = Move 2 Power
        //Y = Move 2 Stab
        //Z = Move 2 Crit Chance
        //AA = Move 2 Speed
        //AB = Move 2 Energy
        //AC = Weave Cycle Damage
        //AD = Average Weave Cycle Length (ms)
        //AF = Average Gym Weave Cycle Length (ms)
        //AJ1 = Crit Damage Bonus
        //AL1 = Charge Delay

        //=IF(AB2=100,CEILING(AB2/U2),AB2/U2)
        final double weaveEnergyUsageRatio;

        if (Math.abs(pm2.getEnergy()) == MAX_MOVE_ENERGY) {
            weaveEnergyUsageRatio = Math.ceil((double) Math.abs(pm2.getEnergy()) / (double) pm1.getEnergy());
        } else {
            weaveEnergyUsageRatio = (double) Math.abs(pm2.getEnergy()) / (double) pm1.getEnergy();
        }

        //=IF(AB2=100,CEILING(AB2/U2),AB2/U2)*T2+(AA2+$AL$1)
        //=IF(AB2=100,CEILING(AB2/U2),AB2/U2)*(T2+2000)+(AA2+$AL$1)
        final double weaveCycleLength = weaveEnergyUsageRatio
            * (pm1.getTime() + additionalDelay)
            + pm2.getTime() + PokemonCalculationUtils.MOVE2_CHARGE_DELAY_MS;

        //=FLOOR(100000/AD2)
        //*(X2*(1+Y2*0.25) * (1+($AJ$1*Z2/100)))
        //+CEILING(FLOOR(100000/AD2)*IF(AB2=100,CEILING(AB2/U2),AB2/U2))
        //*(R2*(1+(S2*0.25)))
        //+FLOOR((100000-(FLOOR(100000/AD2)*(AA2+$AL$1)+CEILING(FLOOR(100000/AD2)*IF(AB2=100,CEILING(AB2/U2),AB2/U2))*T2))/T2)
        //*(R2*(1+(S2*0.25)))
        //=FLOOR(100000/AF2)*(X2*(1+Y2*0.25)*(1+($AJ$1*Z2/100)))+CEILING(FLOOR(100000/AF2)*IF(AB2=100,CEILING(AB2/U2),AB2/U2))*(R2*(1+(S2*0.25)))
        //  +FLOOR((100000-(FLOOR(100000/AF2)*(AA2+$AL$1)+CEILING(FLOOR(100000/AF2)*IF(AB2=100,CEILING(AB2/U2),AB2/U2))*(T2+2000)))/(T2+2000))*(R2*(1+(S2*0.25)))
        final double floorThingyCalculation = (
            WEAVE_NUMBER - (
                Math.floor(WEAVE_NUMBER / weaveCycleLength) * (pm2.getTime()
                    + PokemonCalculationUtils.MOVE2_CHARGE_DELAY_MS)
                    + Math.ceil(Math.floor(WEAVE_NUMBER / weaveCycleLength) * weaveEnergyUsageRatio) * (pm1.getTime() + additionalDelay)
            )
        ) / (pm1.getTime() + additionalDelay);

        //noinspection UnnecessaryLocalVariable
        final double weaveDPS = Math.floor(WEAVE_NUMBER / weaveCycleLength)
            * (pm2.getPower() * moveTwoStab * (1 + (PokemonCalculationUtils.CRIT_DAMAGE_BONUS * pm2.getCritChance())))
            + Math.ceil(Math.floor(WEAVE_NUMBER / weaveCycleLength) * weaveEnergyUsageRatio)
            * (pm1.getPower() * moveOneStab)
            + Math.floor(floorThingyCalculation)
            * (pm1.getPower() * moveOneStab);

        return weaveDPS;
    }
}