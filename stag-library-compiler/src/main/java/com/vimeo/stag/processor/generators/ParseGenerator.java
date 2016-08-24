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
package com.vimeo.stag.processor.generators;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.vimeo.stag.GsonAdapterKey;
import com.vimeo.stag.processor.generators.model.AnnotatedClass;
import com.vimeo.stag.processor.generators.model.ClassInfo;
import com.vimeo.stag.processor.generators.model.SupportedTypesModel;
import com.vimeo.stag.processor.utils.FileGenUtils;
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

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@SuppressWarnings("StringConcatenationMissingWhitespace")
public class ParseGenerator {

    private static final String CLASS_PARSE_UTILS = "ParseUtils";

    @NotNull
    private final Set<String> mSupportedTypes;

    @NotNull
    private final Filer mFiler;

    public ParseGenerator(@NotNull Set<String> supportedTypes, @NotNull Filer filer) {
        mSupportedTypes = new HashSet<>(supportedTypes);
        mFiler = filer;
    }

    @NotNull
    private static MethodSpec generateParseMapSpec() {
        TypeVariableName genericTypeName = TypeVariableName.get("T");

        return MethodSpec.methodBuilder("parseMap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariable(genericTypeName)
                .returns(ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(String.class),
                                                   genericTypeName))
                .addParameter(Gson.class, "gson")
                .addParameter(JsonReader.class, "reader")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), genericTypeName), "clazz")
                .addException(IOException.class)
                .addCode("if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_OBJECT) {\n" +
                         "\treader.skipValue();\n" +
                         "\treturn null;\n" +
                         "}\n" +
                         "reader.beginObject();\n" +
                         "\n" +
                         "HashMap<String, T> list = Stag.readMapFromAdapter(gson, clazz, reader);\n" +
                         "\n" +
                         "reader.endObject();\n" +
                         "return list;\n")
                .build();
    }

    @NotNull
    private static MethodSpec generateWriteMapSpec() {
        TypeVariableName genericType = TypeVariableName.get("T");
        return MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addTypeVariable(genericType)
                .addException(IOException.class)
                .addParameter(Gson.class, "gson")
                .addParameter(JsonWriter.class, "writer")
                .addParameter(Class.class, "clazz")
                .addParameter(
                        ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(String.class),
                                                  genericType), "map")
                .addCode("writer.beginObject();\n" +
                         "if (map != null) {\n" +
                         "\tStag.writeMapToAdapter(gson, clazz, writer, map);\n" +
                         "}\n" +
                         "\n" +
                         "writer.beginObject();\n")
                .build();
    }

    @NotNull
    private static MethodSpec generateParseArraySpec() {

        TypeVariableName genericTypeName = TypeVariableName.get("T");

        return MethodSpec.methodBuilder("parseArray")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariable(genericTypeName)
                .returns(ParameterizedTypeName.get(ClassName.get(ArrayList.class), genericTypeName))
                .addParameter(Gson.class, "gson")
                .addParameter(JsonReader.class, "reader")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), genericTypeName), "clazz")
                .addException(IOException.class)
                .addCode("if(reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {\n" +
                         "\treader.skipValue();\n" +
                         "\treturn null;\n}\n" +
                         "reader.beginArray();\n" +
                         '\n' +
                         "ArrayList<" + genericTypeName.name + "> list = " + StagGenerator.CLASS_STAG +
                         ".readListFromAdapter(gson, clazz, reader);\n" +
                         '\n' +
                         "reader.endArray();\n" +
                         "return list;\n")
                .build();
    }

    @NotNull
    private static MethodSpec generateWriteArraySpec() {
        TypeVariableName genericType = TypeVariableName.get("T");
        return MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addTypeVariable(genericType)
                .addException(IOException.class)
                .addParameter(Gson.class, "gson")
                .addParameter(JsonWriter.class, "writer")
                .addParameter(Class.class, "clazz")
                .addParameter(ParameterizedTypeName.get(ClassName.get(ArrayList.class), genericType), "list")
                .addCode("writer.beginArray();\n" +
                         "if (list != null) {\n" +
                         "\tStag.writeListToAdapter(gson, clazz, writer, list);\n" +
                         "}\n" +
                         '\n' +
                         "writer.endArray();\n")
                .build();
    }

    /**
     * Generates the ParseUtils class. This class includes
     * parsing/write methods for JsonArray -> ArrayList, and
     * the parsing/write methods for all objects supported by
     * the Stag library.
     *
     * @throws IOException thrown if we are unable to write
     *                     to the source file for ParseUtils. Most likely the
     *                     file is being held by another process, barring us from
     *                     modifying it.
     */
    public void generateParsingCode() throws IOException {
        TypeSpec.Builder typeSpecBuilder =
                TypeSpec.classBuilder(CLASS_PARSE_UTILS).addModifiers(Modifier.FINAL);

        typeSpecBuilder.addMethod(ParseGenerator.generateParseMapSpec());
        typeSpecBuilder.addMethod(ParseGenerator.generateWriteMapSpec());
        typeSpecBuilder.addMethod(ParseGenerator.generateParseArraySpec());
        typeSpecBuilder.addMethod(ParseGenerator.generateWriteArraySpec());

        List<AnnotatedClass> list = SupportedTypesModel.getInstance().getSupportedTypes();

        for (AnnotatedClass entry : list) {
            if (TypeUtils.isConcreteType(entry.getElement().asType())) {
                Map<Element, TypeMirror> memberVariables = entry.getMemberVariables();
                typeSpecBuilder.addMethod(generateWriteSpec(entry.getType(), memberVariables));
                typeSpecBuilder.addMethod(generateParseSpec(entry.getType(), memberVariables));
            }
        }

        JavaFile javaFile =
                JavaFile.builder(FileGenUtils.GENERATED_PACKAGE_NAME, typeSpecBuilder.build()).build();

        FileGenUtils.writeToFile(javaFile, mFiler);
    }

    @NotNull
    private MethodSpec generateWriteSpec(TypeMirror type, Map<Element, TypeMirror> elements) {
        MethodSpec.Builder writeBuilder = MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Gson.class, "gson")
                .addParameter(JsonWriter.class, "writer")
                .addParameter(ClassName.get(type), "object")
                .addException(IOException.class)
                .returns(void.class)
                .addCode("\twriter.beginObject();\n" + "\tif (object != null) {\n");

        for (Entry<Element, TypeMirror> element : elements.entrySet()) {
            String name = getJsonName(element.getKey());
            String variableName = element.getKey().getSimpleName().toString();
            String variableType = element.getValue().toString();

            boolean isPrimitive = isPrimitive(variableType);

            if (!isPrimitive) {
                writeBuilder.addCode("\t\tif (object." + variableName + " != null) {\n");
            }
            writeBuilder.addCode("\t\t\twriter.name(\"" + name + "\");\n");
            writeBuilder.addCode("\t\t\t" + getWriteType(element.getValue(), variableName) + '\n');
            if (!isPrimitive) {
                writeBuilder.addCode("\t\t}\n");
            }
        }
        writeBuilder.addCode("\t}\n" + "\twriter.endObject();\n");

        return writeBuilder.build();
    }

    @NotNull
    private static String getJsonName(Element element) {
        String name = element.getAnnotation(GsonAdapterKey.class).value();

        if (name == null || name.isEmpty()) {
            name = element.getSimpleName().toString();
        }
        return name;
    }

    @NotNull
    private MethodSpec generateParseSpec(TypeMirror type, Map<Element, TypeMirror> elements) {
        ClassInfo info = new ClassInfo(type);

        MethodSpec.Builder parseBuilder = MethodSpec.methodBuilder("parse" + info.getClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(info.getType()))
                .addParameter(Gson.class, "gson")
                .addParameter(JsonReader.class, "reader")
                .addException(IOException.class)
                .addCode("\tcom.google.gson.stream.JsonToken token = reader.peek();\n" +
                         "\tif (token == com.google.gson.stream.JsonToken.NULL) {\n" +
                         "\t\treader.nextNull();\n" +
                         "\t\treturn null;\n" +
                         "\t}\n" +
                         "\tif(token != com.google.gson.stream.JsonToken.BEGIN_OBJECT) {\n" +
                         "\t\treader.skipValue();\n" +
                         "\t\treturn null;\n" +
                         "\t}\n" +
                         "\treader.beginObject();\n" +
                         '\n' +
                         '\t' + info.getClassAndPackage() + " object = new " + info.getClassAndPackage() +
                         "();\n" +
                         "\twhile (reader.hasNext()) {\n" +
                         "\t\tString name = reader.nextName();\n" +
                         "\t\tcom.google.gson.stream.JsonToken jsonToken = reader.peek();\n" +
                         "\t\tif (jsonToken == com.google.gson.stream.JsonToken.NULL) {\n" +
                         "\t\t\treader.skipValue();\n" +
                         "\t\t\tcontinue;\n" +
                         "\t\t}\n" +
                         "\t\tswitch (name) {\n");

        for (Entry<Element, TypeMirror> element : elements.entrySet()) {
            String name = getJsonName(element.getKey());

            String variableName = element.getKey().getSimpleName().toString();
            String jsonTokenType = getReadTokenType(element.getValue());

            if (jsonTokenType != null) {
                parseBuilder.addCode("\t\t\tcase \"" + name + "\":\n" +
                                     "\t\t\t\tif(jsonToken == " + jsonTokenType +
                                     ") {\n" +
                                     "\t\t\t\t\tobject." + variableName + " = " +
                                     getReadType(element.getValue()) +
                                     "\n\t\t\t\t} else {" +
                                     "\n\t\t\t\t\treader.skipValue();" +
                                     "\n\t\t\t\t}" +
                                     '\n' +
                                     "\t\t\t\tbreak;\n");
            } else {
                parseBuilder.addCode("\t\t\tcase \"" + name + "\":\n" +
                                     "\t\t\t\ttry {\n" +
                                     "\t\t\t\t\tobject." + variableName + " = " +
                                     getReadType(element.getValue()) +
                                     "\n\t\t\t\t} catch(Exception exception) {" +
                                     "\n\t\t\t\t\tthrow new IOException(\"Error parsing " +
                                     info.getClassName() + "." + variableName + " JSON!\", exception);" +
                                     "\n\t\t\t\t}" +
                                     '\n' +
                                     "\t\t\t\tbreak;\n");
            }
        }

        parseBuilder.addCode("\t\t\tdefault:\n" +
                             "\t\t\t\treader.skipValue();\n" +
                             "\t\t\t\tbreak;\n" +
                             "\t\t}\n" +
                             "\t}\n" +
                             '\n' +
                             "\treader.endObject();\n" +
                             "\treturn object;\n");

        return parseBuilder.build();
    }

    @Nullable
    private static String getReadTokenType(@NotNull TypeMirror type) {
        if (TypeUtils.doClassesMatch(type, long.class)) {
            return "com.google.gson.stream.JsonToken.NUMBER";
        } else if (TypeUtils.doClassesMatch(type, double.class)) {
            return "com.google.gson.stream.JsonToken.NUMBER";
        } else if (TypeUtils.doClassesMatch(type, boolean.class)) {
            return "com.google.gson.stream.JsonToken.BOOLEAN";
        } else if (TypeUtils.doClassesMatch(type, String.class)) {
            return "com.google.gson.stream.JsonToken.STRING";
        } else if (TypeUtils.doClassesMatch(type, int.class)) {
            return "com.google.gson.stream.JsonToken.NUMBER";
        } else if (TypeUtils.doClassesMatch(type, ArrayList.class)) {
            return "com.google.gson.stream.JsonToken.BEGIN_ARRAY";
        } else if (TypeUtils.doClassesMatch(type, HashMap.class)) {
            return "com.google.gson.stream.JsonToken.BEGIN_OBJECT";
        } else {
            return null;
        }

    }

    @NotNull
    private String getReadType(@NotNull TypeMirror type) {
        if (TypeUtils.doClassesMatch(type, long.class)) {
            return "reader.nextLong();";
        } else if (TypeUtils.doClassesMatch(type, double.class)) {
            return "reader.nextDouble();";
        } else if (TypeUtils.doClassesMatch(type, boolean.class)) {
            return "reader.nextBoolean();";
        } else if (TypeUtils.doClassesMatch(type, String.class)) {
            return "reader.nextString();";
        } else if (TypeUtils.doClassesMatch(type, int.class)) {
            return "reader.nextInt();";
        } else if (TypeUtils.doClassesMatch(type, ArrayList.class)) {
            return "ParseUtils.parseArray(gson, reader, " + getInnerListType(type).toString() + ".class);";
        } else if (TypeUtils.doClassesMatch(type, HashMap.class)) {
            return "ParseUtils.parseMap(gson, reader, " + getInnerMapValueType(type).toString() + ".class);";
        } else {
            String typeName = type.toString();
            if (!mSupportedTypes.contains(type.toString())) {
                return StagGenerator.CLASS_STAG + ".readFromAdapter(gson, " + typeName + ".class, reader);";
            } else {
                ClassInfo info = new ClassInfo(type);
                return "ParseUtils.parse" + info.getClassName() + "(gson, reader);";
            }
        }
    }

    @NotNull
    private String getWriteType(@NotNull TypeMirror type, @NotNull String variableName) {
        if (TypeUtils.doClassesMatch(type, long.class) ||
            TypeUtils.doClassesMatch(type, double.class) ||
            TypeUtils.doClassesMatch(type, boolean.class) ||
            TypeUtils.doClassesMatch(type, String.class) ||
            TypeUtils.doClassesMatch(type, int.class)) {
            return "writer.value(object." + variableName + ");";
        } else if (TypeUtils.doClassesMatch(type, ArrayList.class)) {
            return "ParseUtils.write(gson, writer, " + getInnerListType(type).toString() + ".class, object." +
                   variableName + ");";
        } else if (TypeUtils.doClassesMatch(type, HashMap.class)) {
            return "ParseUtils.write(gson, writer, " + getInnerMapValueType(type).toString() +
                   ".class, object." + variableName + ");";
        } else {
            if (!mSupportedTypes.contains(type.toString())) {
                return StagGenerator.CLASS_STAG + ".writeToAdapter(gson, " + type +
                       ".class, writer, object." +
                       variableName + ");";
            } else {
                return "ParseUtils.write(gson, writer, object." + variableName + ");";
            }
        }
    }

    private static boolean isPrimitive(@NotNull String type) {
        return type.equals(long.class.getName()) ||
               type.equals(double.class.getName()) ||
               type.equals(boolean.class.getName()) ||
               type.equals(int.class.getName());
    }

    private static TypeMirror getInnerListType(@NotNull TypeMirror type) {
        return ((DeclaredType) type).getTypeArguments().get(0);
    }

    private static TypeMirror getInnerMapValueType(@NotNull TypeMirror type) {
        return ((DeclaredType) type).getTypeArguments().get(1);
    }

}
