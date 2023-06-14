/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModOriginImpl;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class ModContainerImpl extends net.fabricmc.loader.ModContainer {
	private final IModInfo modInfo;
	private final LoaderModMetadata metadata;
	private final ModOrigin origin;
	private final Collection<String> childModIds;

	public ModContainerImpl(IModInfo modInfo) {
		this.modInfo = modInfo;
		this.metadata = Objects.requireNonNull((LoaderModMetadata) modInfo.getOwningFile().getFileProperties().get("metadata"));
		this.origin = new ModOriginImpl(getRootPaths());
		this.childModIds = modInfo.getOwningFile().getMods().stream().filter(other -> other != modInfo).map(IModInfo::getDisplayName).collect(Collectors.toSet());
	}

	@Override
	public LoaderModMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public ModOrigin getOrigin() {
		return origin;
	}

	@Override
	public List<Path> getCodeSourcePaths() {
		return getRootPaths();
	}

	@Override
	public Path getRootPath() {
		return this.modInfo.getOwningFile().getFile().findResource(".");
	}

	@Override
	public List<Path> getRootPaths() {
		return List.of(getRootPath());
	}

	@Override
	public Path getPath(String file) {
		return this.modInfo.getOwningFile().getFile().findResource(file);
	}

	@Override
	public Optional<ModContainer> getContainingMod() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<ModContainer> getContainedMods() {
		if (this.childModIds.isEmpty()) return Collections.emptyList();

		List<ModContainer> ret = new ArrayList<>(this.childModIds.size());

		for (String id : this.childModIds) {
			FabricLoaderImpl.INSTANCE.getModContainer(id).ifPresent(ret::add);
		}

		return ret;
	}

	@Deprecated
	@Override
	public LoaderModMetadata getInfo() {
		return getMetadata();
	}

	@Override
	public String toString() {
		return String.format("%s %s", this.modInfo.getModId(), this.modInfo.getVersion());
	}
}
