package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small factories for the option kinds the settings screen uses; names come from {@link Texts}. */
final class Options {
	private Options() {
	}

	static Option<Boolean> bool(String path, boolean fallback, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return named(Option.<Boolean>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(TickBoxControllerBuilder::create)
			.build();
	}

	static <E extends Enum<E>> Option<E> choice(String path, Class<E> type, E fallback, Supplier<E> getter, Consumer<E> setter) {
		return named(Option.<E>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(option -> EnumControllerBuilder.create(option).enumClass(type).formatValue(Texts::enumValue))
			.build();
	}

	static Option<Integer> intField(String path, int fallback, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
		return named(Option.<Integer>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(option -> IntegerFieldControllerBuilder.create(option).range(min, max))
			.build();
	}

	static Option<Integer> intSlider(String path, int fallback, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
		return named(Option.<Integer>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(option -> IntegerSliderControllerBuilder.create(option).range(min, max).step(1))
			.build();
	}

	static Option<Double> doubleField(String path, double fallback, double min, double max, Supplier<Double> getter, Consumer<Double> setter) {
		return named(Option.<Double>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(option -> DoubleFieldControllerBuilder.create(option).range(min, max))
			.build();
	}

	static Option<Double> slider(String path, double fallback, double min, double max, double step, Supplier<Double> getter, Consumer<Double> setter) {
		return named(Option.<Double>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(option -> DoubleSliderControllerBuilder.create(option).range(min, max).step(step))
			.build();
	}

	static Option<String> text(String path, String fallback, Supplier<String> getter, Consumer<String> setter) {
		return named(Option.<String>createBuilder(), path)
			.binding(fallback, getter, setter)
			.controller(StringControllerBuilder::create)
			.build();
	}

	static OptionDescription description(String path) {
		return OptionDescription.of(Texts.description(path));
	}

	private static <T> Option.Builder<T> named(Option.Builder<T> builder, String path) {
		return builder.name(Texts.name(path)).description(description(path));
	}
}
