package net.minestom.server.resourcepack;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.HashMap;

@Getter
@EqualsAndHashCode
public class Variants {

	HashMap<String, HashMap<String, String>> variants = new HashMap<>();

}
