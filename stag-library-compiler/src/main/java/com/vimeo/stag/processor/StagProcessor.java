/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 Vimeo
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vimeo.stag.processor;

import com.google.auto.service.AutoService;
import com.vimeo.stag.GsonAdapterKey;
import com.vimeo.stag.processor.generators.ParseGenerator;
import com.vimeo.stag.processor.generators.StagGenerator;
import com.vimeo.stag.processor.generators.model.AnnotatedClass;
import com.vimeo.stag.processor.utils.DebugLog;
import com.vimeo.stag.processor.generators.model.SupportedTypesModel;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.vimeo.stag.GsonAdapterKey")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public final class StagProcessor extends AbstractProcessor {

    public static final boolean DEBUG = false;
    private boolean mHasBeenProcessed;
    private final Set<String> mSupportedTypes = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new HashSet<>();
        set.add(GsonAdapterKey.class.getCanonicalName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (mHasBeenProcessed) {
            return true;
        }
        TypeUtils.initialize(processingEnv.getTypeUtils());

        DebugLog.log("\nBeginning @GsonAdapterKey annotation processing\n");

        mHasBeenProcessed = true;
        Map<Element, List<VariableElement>> variableMap = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(GsonAdapterKey.class)) {
            if (element instanceof VariableElement) {
                final VariableElement variableElement = (VariableElement) element;

                Set<Modifier> modifiers = variableElement.getModifiers();
                if (modifiers.contains(Modifier.FINAL)) {
                    throw new RuntimeException("Unable to access field \"" +
                                               variableElement.getSimpleName().toString() + "\" in class " +
                                               variableElement.getEnclosingElement().asType() +
                                               ", field must not be final.");
                } else if (!modifiers.contains(Modifier.PUBLIC)) {
                    throw new RuntimeException("Unable to access field \"" +
                                               variableElement.getSimpleName().toString() + "\" in class " +
                                               variableElement.getEnclosingElement().asType() +
                                               ", field must public.");
                }

                Element enclosingClassElement = variableElement.getEnclosingElement();
                TypeMirror enclosingClass = enclosingClassElement.asType();

                if (!TypeUtils.isParameterizedType(enclosingClass) ||
                    TypeUtils.isConcreteType(enclosingClass)) {
                    mSupportedTypes.add(enclosingClass.toString());
                }

                addToListMap(variableMap, enclosingClassElement, variableElement);
            } else if (element instanceof TypeElement) {
                mSupportedTypes.add(element.asType().toString());
                addToListMap(variableMap, element, null);
            }
        }

        try {
            for (Entry<Element, List<VariableElement>> entry : variableMap.entrySet()) {
                SupportedTypesModel.getInstance()
                        .addSupportedType(new AnnotatedClass(entry.getKey(), entry.getValue()));
            }
            ParseGenerator parseGenerator = new ParseGenerator(mSupportedTypes, processingEnv.getFiler());
            parseGenerator.generateParsingCode();
            StagGenerator adapterGenerator = new StagGenerator(processingEnv.getFiler());
            adapterGenerator.generateTypeAdapters();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DebugLog.log("\nSuccessfully processed @GsonAdapterKey annotations\n");

        return true;
    }

    private static void addToListMap(@NotNull Map<Element, List<VariableElement>> map, @Nullable Element key,
                                     @Nullable VariableElement value) {
        if (key == null) {
            return;
        }
        List<VariableElement> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>(1);
        }
        if (value != null) {
            list.add(value);
        }
        map.put(key, list);
    }

}
