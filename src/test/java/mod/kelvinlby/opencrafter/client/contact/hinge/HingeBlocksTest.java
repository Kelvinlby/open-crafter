package mod.kelvinlby.opencrafter.client.contact.hinge;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HingeBlocksTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void aPlacedDoorIsNoToggle() {
		assertFalse(HingeBlocks.toggled(Blocks.AIR.defaultBlockState(), door(false)));
	}

	@Test
	void openingADoorIsAToggle() {
		assertTrue(HingeBlocks.toggled(door(false), door(true)));
	}

	@Test
	void theOtherHalfTakesTheOldOpenState() {
		BlockState upperNow = door(true).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		BlockState upperBefore = HingeBlocks.withOpenOf(upperNow, door(false));
		assertFalse(upperBefore.getValue(BlockStateProperties.OPEN));
		assertEquals(BlockPos.ZERO.above(), HingeBlocks.otherHalf(BlockPos.ZERO, door(true)));
	}

	private static BlockState door(boolean open) {
		return Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, open);
	}
}
