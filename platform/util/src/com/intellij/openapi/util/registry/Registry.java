// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a UI to configure internal settings of the IDE.
 * <p>
 * Plugins can provide their own registry keys using the
 * {@code com.intellij.registryKey} extension point (see {@link RegistryKeyBean} for more details).
 */
public final class Registry  {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  public static final String REGISTRY_BUNDLE = "misc.registry";

  private final Map<String, String> myUserProperties = new LinkedHashMap<>();
  private final Map<String, RegistryValue> myValues = new ConcurrentHashMap<>();
  private final Map<String, RegistryKeyDescriptor> myContributedKeys = new HashMap<>();

  private static final Registry ourInstance = new Registry();
  private volatile boolean myLoaded;

  public static @NotNull RegistryValue get(@NonNls @NotNull String key) {
    return getInstance().doGet(key);
  }

  private @NotNull RegistryValue doGet(@NonNls @NotNull String key) {
    RegistryValue value = myValues.get(key);
    if (value != null) {
      return value;
    }

    value = new RegistryValue(this, key, myContributedKeys.get(key));
    RegistryValue prev = myValues.putIfAbsent(key, value);
    return prev == null ? value : prev;
  }

  public static boolean is(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asBoolean();
  }

  public static boolean is(@NonNls @NotNull String key, boolean defaultValue) {
    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      LoadingState.LAF_INITIALIZED.checkOccurred();
      return defaultValue;
    }

    try {
      return get(key).asBoolean();
    }
    catch (MissingResourceException ignore) {
      return defaultValue;
    }
  }

  public static int intValue(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asInteger();
  }

  public static int intValue(@NonNls @NotNull String key, int defaultValue) {
    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      LoadingState.LAF_INITIALIZED.checkOccurred();
      return defaultValue;
    }

    try {
      return get(key).asInteger();
    }
    catch (MissingResourceException ignore) {
      return defaultValue;
    }
  }

  public static double doubleValue(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asDouble();
  }

  public static @NotNull String stringValue(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asString();
  }

  public static Color getColor(@NonNls @NotNull String key, Color defaultValue) throws MissingResourceException {
    return get(key).asColor(defaultValue);
  }

  static @NotNull ResourceBundle getBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(REGISTRY_BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }

  public @NlsSafe String getBundleValue(@NonNls @NotNull String key, boolean mustExist) throws MissingResourceException {
    if (myContributedKeys.containsKey(key)) {
      return myContributedKeys.get(key).getDefaultValue();
    }

    try {
      return getBundle().getString(key);
    }
    catch (MissingResourceException e) {
      if (mustExist) {
        throw e;
      }
    }

    return null;
  }

  public static @NotNull Registry getInstance() {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();
    return ourInstance;
  }

  public @NotNull Element getState() {
    final Element state = new Element("registry");
    for (String eachKey : myUserProperties.keySet()) {
      final Element entry = new Element("entry");
      entry.setAttribute("key", eachKey);
      entry.setAttribute("value", myUserProperties.get(eachKey));
      state.addContent(entry);
    }
    return state;
  }

  @ApiStatus.Internal
  public void loadState(@NotNull Element state) {
    myUserProperties.clear();
    for (Element eachEntry : state.getChildren("entry")) {
      String key = eachEntry.getAttributeValue("key");
      String value = eachEntry.getAttributeValue("value");
      if (key != null && value != null) {
        RegistryValue registryValue = doGet(key);
        if (registryValue.isChangedFromDefault(value, this)) {
          myUserProperties.put(key, value);
          registryValue.resetCache();
        }
      }
    }
    markAsLoaded();
  }

  @ApiStatus.Internal
  public void markAsLoaded() {
    myLoaded = true;
  }

  public boolean isLoaded() {
    return myLoaded;
  }

  @ApiStatus.Internal
  public @NotNull Map<String, String> getUserProperties() {
    return myUserProperties;
  }

  public static @NotNull List<RegistryValue> getAll() {
    final ResourceBundle bundle = getBundle();
    final Enumeration<String> keys = bundle.getKeys();

    List<RegistryValue> result = new ArrayList<>();

    Registry instance = getInstance();
    Map<String, RegistryKeyDescriptor> contributedKeys = instance.myContributedKeys;
    while (keys.hasMoreElements()) {
      @NonNls final String each = keys.nextElement();
      if (each.endsWith(".description") || each.endsWith(".restartRequired") || contributedKeys.containsKey(each)) {
        continue;
      }
      result.add(instance.doGet(each));
    }

    for (String key : contributedKeys.keySet()) {
      result.add(instance.doGet(key));
    }

    return result;
  }

  void restoreDefaults() {
    Map<String, String> old = new HashMap<>(myUserProperties);
    Registry instance = getInstance();
    for (String each : old.keySet()) {
      try {
        instance.doGet(each).resetToDefault();
      }
      catch (MissingResourceException e) {
        // outdated property that is not declared in registry.properties anymore
        myValues.remove(each);
      }
    }
  }

  boolean isInDefaultState() {
    return myUserProperties.isEmpty();
  }

  public boolean isRestartNeeded() {
    return isRestartNeeded(myUserProperties);
  }

  private static boolean isRestartNeeded(@NotNull Map<String, String> map) {
    Registry instance = getInstance();
    for (String s : map.keySet()) {
      RegistryValue eachValue = instance.doGet(s);
      if (eachValue.isRestartRequired() && eachValue.isChangedSinceAppStart()) {
        return true;
      }
    }

    return false;
  }

  public static synchronized void addKeys(@NotNull Iterator<RegistryKeyDescriptor> descriptors) {
    // getInstance must be not used here - phase COMPONENT_REGISTERED is not yet completed
    while (descriptors.hasNext()) {
      RegistryKeyDescriptor descriptor = descriptors.next();
      ourInstance.myContributedKeys.put(descriptor.getName(), descriptor);
    }
  }

  public static synchronized void removeKey(@NonNls @NotNull String key) {
    ourInstance.myContributedKeys.remove(key);
    ourInstance.myValues.remove(key);
  }
}
