// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;

import static com.intellij.packaging.jlink.JLinkArtifactProperties.CompressionLevel;

final class JLinkArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JLinkArtifactProperties myProperties;

  private ComboBox<String> myCompressionLevel;
  private JCheckBox myVerbose;

  JLinkArtifactPropertiesEditor(@NotNull JLinkArtifactProperties properties) {
    myProperties = properties;
  }

  @Nls
  @Override
  public String getTabName() {
    return JavaBundle.message("packaging.jlink.artifact.name");
  }

  @Override
  public @Nullable JComponent createComponent() {
    final FormBuilder builder = new FormBuilder();

    myCompressionLevel = new ComboBox<>(ContainerUtil.map(CompressionLevel.values(), t -> t.myText).toArray(String[]::new));
    CompressionLevel compressionLevel = myProperties.compressionLevel;
    if (compressionLevel == null) {
      compressionLevel = CompressionLevel.ZERO;
    }
    myCompressionLevel.setItem(compressionLevel.myText);
    builder.addLabeledComponent(JavaBundle.message("packaging.jlink.compression.level"), myCompressionLevel);

    myVerbose = new JCheckBox(JavaBundle.message("packaging.jlink.verbose.tracing"), myProperties.verbose);
    builder.addComponent(myVerbose);

    return builder.getPanel();
  }

  @Override
  public boolean isModified() {
    if (myProperties.compressionLevel != CompressionLevel.getLevelByText(myCompressionLevel.getItem())) return true;
    if (myProperties.verbose != myVerbose.isSelected()) return true;
    return false;
  }

  @Override
  public void apply() {
    myProperties.compressionLevel = Optional.ofNullable(myCompressionLevel.getItem())
      .map(i -> CompressionLevel.getLevelByText(i))
      .orElse(CompressionLevel.ZERO);
    myProperties.verbose = myVerbose.isSelected();
  }
}
