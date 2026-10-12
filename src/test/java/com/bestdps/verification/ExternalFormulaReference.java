package com.bestdps.verification;

import com.bestdps.calc.RollMath;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Original adapter: loads a separately compiled Combat Insight checkout without bundling it. */
public final class ExternalFormulaReference
{
	public static void main(String[] args) throws Exception
	{
		int checks = 0;
		try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, null))
		{
			Class<?> prediction = loader.loadClass("com.combatinsight.calculation.CombatPrediction");
			Method normal = prediction.getMethod("hitChance",int.class,int.class);
			Method fang = prediction.getMethod("fangHitChance",int.class,int.class);
			Method damage = loader.loadClass("com.combatinsight.calculation.DamageRoll").getMethod("averageSuccessfulHit",int.class,int.class,int.class);
			int[] rolls = {-100000,-1000,-65,-64,-3,-2,-1,0,1,2,3,63,64,65,100,1000,10000,100000};
			for (int attack : rolls) for (int defence : rolls)
			{
				check("normal " + attack + "/" + defence, (double)normal.invoke(null,attack,defence),RollMath.normalAccuracy(attack,defence));
				check("fang " + attack + "/" + defence, (double)fang.invoke(null,attack,defence),RollMath.fangAccuracy(attack,defence));
				checks += 2;
			}
			for (int maximum=1; maximum<=150; maximum++) for (int minimum : new int[]{0,maximum/2,maximum})
				for (int armour : new int[]{-100,-5,-1,0,1,3,10,20,100})
				{
					check("damage " + minimum + "/" + maximum + "/" + armour,
						(double)damage.invoke(null,minimum,maximum,armour),RollMath.expectedHitAfterArmour(1,minimum,maximum,armour));
					checks++;
				}
		}
		System.out.println("Combat Insight independent formula comparisons passed: " + checks);
	}

	private static void check(String input,double reference,double actual)
	{
		if (!Double.isFinite(actual) || Math.abs(reference-actual)>1e-10)
			throw new AssertionError(input + ": reference=" + reference + " plugin=" + actual);
	}
}
