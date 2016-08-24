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

import com.google.testing.compile.CompilationRule;
import com.vimeo.stag.processor.dummy.DummyConcreteClass;
import com.vimeo.stag.processor.dummy.DummyGenericClass;
import com.vimeo.stag.processor.dummy.DummyInheritedClass;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the annotation processor.
 * Run using: {@code ./gradlew :stag-library-compiler:test --continue}
 */
public class TypeUtilsUnitTest {

    @Rule
    public CompilationRule rule = new CompilationRule();
    private Elements elements;
    private Types types;


    @Before
    public void setup() {
        elements = rule.getElements();
        types = rule.getTypes();

        TypeUtils.initialize(types);
    }

    @Test
    public void testFinalClass_constructorFails() throws Exception {
        Utils.testZeroArgumentConstructorFinalClass(TypeUtils.class);
    }

    @Test
    public void testInitialize_works() throws Exception {
        // Initialize it to null in order to test correctly
        //noinspection ConstantConditions
        TypeUtils.initialize(null);

        boolean exceptionThrown = false;

        try {
            TypeUtils.getUtils();
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);

        TypeUtils.initialize(types);
        assertNotNull(TypeUtils.getUtils());
    }

    @Test
    public void getInheritedType_isCorrect() throws Exception {
        TypeMirror concreteType = TypeUtils.getInheritedType(getElementFromClass(DummyInheritedClass.class));
        assertNotNull(concreteType);

        TypeMirror realConcreteType =
                types.getDeclaredType((TypeElement) getElementFromClass(DummyGenericClass.class),
                                      getTypeMirrorFromClass(String.class));

        assertTrue(realConcreteType.toString().equals(concreteType.toString()));

        TypeMirror stringInheritedType = TypeUtils.getInheritedType(getElementFromClass(String.class));
        assertNull(stringInheritedType);
    }

    /**
     * This test is particularly susceptible to changes
     * in the DummyGenericClass. Any fields
     * added, removed, renamed, or changed, will probably
     * break this test either explicitly, or implicitly.
     * Any changes to that class need to be reflected here.
     *
     * @throws Exception thrown if the test fails.
     */
    @Test
    public void getConcreteMembers_isCorrect() throws Exception {
        Element genericElement = getElementFromClass(DummyGenericClass.class);
        assertNotNull(genericElement);
        Map<Element, TypeMirror> genericMembers = new HashMap<>();
        for (Element element : genericElement.getEnclosedElements()) {
            if (element instanceof VariableElement) {
                genericMembers.put(element, element.asType());
            }
        }

        TypeMirror concreteType = TypeUtils.getInheritedType(getElementFromClass(DummyInheritedClass.class));

        assertNotNull(concreteType);

        TypeMirror genericType = getGenericVersionOfClass(DummyGenericClass.class);

        assertNotNull(genericType);

        Map<Element, TypeMirror> members =
                TypeUtils.getConcreteMembers(concreteType, types.asElement(genericType), genericMembers);


        TypeMirror stringType = getTypeMirrorFromClass(String.class);
        assertNotNull(stringType);

        for (Entry<Element, TypeMirror> entry : members.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("testObject")) {

                assertTrue(entry.getValue().toString().equals(stringType.toString()));

            } else if (entry.getKey().getSimpleName().contentEquals("testList")) {

                assertTrue(entry.getValue()
                                   .toString()
                                   .equals(types.getDeclaredType(
                                           (TypeElement) getElementFromClass(ArrayList.class), stringType)
                                                   .toString()));

            } else if (entry.getKey().getSimpleName().contentEquals("testMap")) {

                assertTrue(entry.getValue()
                                   .toString()
                                   .equals(types.getDeclaredType(
                                           (TypeElement) getElementFromClass(HashMap.class), stringType,
                                           stringType).toString()));

            } else if (entry.getKey().getSimpleName().contentEquals("testSet")) {

                assertTrue(entry.getValue()
                                   .toString()
                                   .equals(types.getDeclaredType(
                                           (TypeElement) getElementFromClass(HashSet.class), stringType)
                                                   .toString()));
            }
        }
    }

    @Test
    public void isParameterizedType_isCorrect() throws Exception {

        Map<String, List<Object>> testMap = new HashMap<>();
        assertTrue(TypeUtils.isParameterizedType(getTypeMirrorFromObject(testMap)));

        List<Object> testList = new ArrayList<>();
        assertTrue(TypeUtils.isParameterizedType(getTypeMirrorFromObject(testList)));

        String testString = "test";
        assertFalse(TypeUtils.isParameterizedType(getTypeMirrorFromObject(testString)));

        Object testObject = new Object();
        assertFalse(TypeUtils.isParameterizedType(getTypeMirrorFromObject(testObject)));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void doClassesMatch_isCorrect() throws Exception {

        // Test parameterized objects
        HashMap<String, String> testMap = new HashMap<>();
        ArrayList<String> testList = new ArrayList<>();

        // Ignore possible nulls from getTypeMirrorFromClass as if it is null this test will fail anyway
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromClass(long.class), long.class));
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromClass(boolean.class), boolean.class));
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromClass(String.class), String.class));
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromClass(int.class), int.class));
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromObject(testMap), HashMap.class));
        assertTrue(TypeUtils.doClassesMatch(getTypeMirrorFromObject(testList), ArrayList.class));

        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromObject(testMap), ArrayList.class));
        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromObject(testList), HashMap.class));
        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromClass(long.class), String.class));
        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromClass(boolean.class), String.class));
        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromClass(int.class), String.class));
        assertFalse(TypeUtils.doClassesMatch(getTypeMirrorFromClass(String.class), int.class));
    }

    @Test
    public void getOuterClassType_isCorrect() throws Exception {

        // Test different objects
        HashMap<String, List<Object>> testMap = new HashMap<>();
        TypeMirror mapMirror = getTypeMirrorFromObject(testMap);
        assertNotNull(mapMirror);
        assertTrue(HashMap.class.getName().equals(TypeUtils.getOuterClassType(mapMirror)));

        ArrayList<Object> testList = new ArrayList<>();
        TypeMirror listMirror = getTypeMirrorFromObject(testList);
        assertNotNull(listMirror);
        assertTrue(ArrayList.class.getName().equals(TypeUtils.getOuterClassType(listMirror)));

        String testString = "test";
        TypeMirror stringMirror = getTypeMirrorFromObject(testString);
        assertNotNull(stringMirror);
        assertTrue(String.class.getName().equals(TypeUtils.getOuterClassType(stringMirror)));

        Object testObject = new Object();
        TypeMirror objectMirror = getTypeMirrorFromObject(testObject);
        assertNotNull(objectMirror);
        assertTrue(Object.class.getName().equals(TypeUtils.getOuterClassType(objectMirror)));

        // Test primitives
        assertTrue(int.class.getName()
                           .equals(TypeUtils.getOuterClassType(types.getPrimitiveType(TypeKind.INT))));
    }

    @Test
    public void isConcreteType_Element_isCorrect() throws Exception {

        Element concreteElement = getElementFromClass(DummyConcreteClass.class);
        assertNotNull(concreteElement);
        for (Element element : concreteElement.getEnclosedElements()) {
            if (element instanceof VariableElement) {
                assertTrue(TypeUtils.isConcreteType(element));
            }
        }

        Element genericElement = getElementFromClass(DummyGenericClass.class);
        assertNotNull(genericElement);
        for (Element element : genericElement.getEnclosedElements()) {
            if (element instanceof VariableElement) {
                if ("testString".equals(element.getSimpleName().toString())) {
                    assertTrue(TypeUtils.isConcreteType(element));
                } else {
                    assertFalse(TypeUtils.isConcreteType(element));
                }
            }
        }

    }

    @Test
    public void isConcreteType_TypeMirror_isCorrect() throws Exception {

        Element concreteElement = getElementFromClass(DummyConcreteClass.class);
        assertNotNull(concreteElement);
        for (Element element : concreteElement.getEnclosedElements()) {
            if (element instanceof VariableElement) {
                assertTrue(TypeUtils.isConcreteType(element.asType()));
            }
        }

        Element genericElement = getElementFromClass(DummyGenericClass.class);
        assertNotNull(genericElement);
        for (Element element : genericElement.getEnclosedElements()) {
            if (element instanceof VariableElement) {
                if ("testString".equals(element.getSimpleName().toString())) {
                    assertTrue(TypeUtils.isConcreteType(element.asType()));
                } else {
                    assertFalse(TypeUtils.isConcreteType(element.asType()));
                }
            }
        }

    }

    @Nullable
    private Element getElementFromClass(@NotNull Class clazz) {
        return elements.getTypeElement(clazz.getName());
    }

    @Nullable
    private TypeMirror getTypeMirrorFromClass(@NotNull Class clazz) {
        if (void.class.equals(clazz)) {
            return types.getNoType(TypeKind.VOID);
        } else if (clazz.isPrimitive()) {
            String primitiveName = clazz.getName().toUpperCase();
            TypeKind primitiveKind = TypeKind.valueOf(primitiveName);
            return types.getPrimitiveType(primitiveKind);
        } else if (clazz.isArray()) {
            TypeMirror componentType = getTypeMirrorFromClass(clazz.getComponentType());
            return types.getArrayType(componentType);
        } else {
            TypeElement element = elements.getTypeElement(clazz.getCanonicalName());
            if (element == null) {
                return null;
            }
            return element.asType();
        }
    }

    @Nullable
    private Element getElementFromObject(@NotNull Object object) {
        return elements.getTypeElement(object.getClass().getName());
    }

    @Nullable
    private TypeMirror getTypeMirrorFromObject(@NotNull Object object) {
        Element element = getElementFromObject(object);
        return element != null ? element.asType() : null;
    }

    @NotNull
    private TypeMirror getGenericVersionOfClass(@NotNull Class clazz) {
        List<? extends TypeParameterElement> params =
                elements.getTypeElement(clazz.getName()).getTypeParameters();
        TypeMirror[] genericTypes = new TypeMirror[params.size()];
        for (int n = 0; n < genericTypes.length; n++) {
            genericTypes[n] = params.get(n).asType();
        }
        return types.getDeclaredType(elements.getTypeElement(DummyGenericClass.class.getName()),
                                     genericTypes);
    }
}