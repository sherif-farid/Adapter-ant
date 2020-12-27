package com.sherif ;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@SupportedAnnotationTypes("com.sherif.adapterlib.CreateAdapter")
public class MainProcessor extends AbstractProcessor {
    private ProcessingEnvironment processingEnvironment;
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        this.typeUtils = processingEnvironment.getTypeUtils();
        this.elementUtils = processingEnvironment.getElementUtils();
        this.filer = processingEnvironment.getFiler();
        this.messager = processingEnvironment.getMessager();
    }

    private void print(String text) {
        messager.printMessage(Diagnostic.Kind.NOTE, text);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annoations, RoundEnvironment roundEnv) {
        print("start log ---------");
        for (Element rootElement : roundEnv.getElementsAnnotatedWith(CreateAdapter.class)) {
            // rootElement is all classes annotated
           CreateAdapter createAdapter = rootElement.getAnnotation(CreateAdapter.class);
            String name = createAdapter.name();
            TypeMirror model = getModel(createAdapter);
            TypeMirror row = getRow(createAdapter);
            TypeName modelName = TypeName.get(model);
            TypeName rowName = TypeName.get(row);
            String pkg = elementUtils.getPackageOf(rootElement).getQualifiedName().toString();
            String source = getJavaFile(pkg, name, modelName, rowName);
            createClass(name, rootElement, source);
        }

        return true;
    }

    private TypeMirror getModel(CreateAdapter annotation) {
        try { annotation.model(); // this should throw
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
        return null; // can this ever happen ??
    }

    private TypeMirror getRow(CreateAdapter annotation) {
        try { annotation.Row(); // this should throw
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
        return null; // can this ever happen ??
    }

    private void createClass(String name, Element element, String source) {
        print("element.getSimpleName : "+element.getSimpleName().toString());
        try {
            JavaFileObject javaFileObject = filer.createSourceFile(name, element);
            Writer writer = javaFileObject.openWriter();
            writer.write(source);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getJavaFile(
            String pkg,
            String name,
            TypeName model,
            TypeName row) {
        final String modelString = model.toString().substring( model.toString().lastIndexOf(".")+1);
        final String rowString = row.toString().substring( row.toString().lastIndexOf(".")+1);

        final String customVhSimpleName = "CustomViewHolder";

        // super class
        ClassName parent = ClassName.get("androidx.recyclerview.widget.RecyclerView",
                "Adapter");
        ClassName vhName = ClassName.get(pkg+"."+name, customVhSimpleName);
        TypeName parentAdapter = ParameterizedTypeName.get(parent, vhName);
        // create private list
        ClassName dataClassName = ClassName.get(ArrayList.class);
        TypeName parameterizedDataList = ParameterizedTypeName.get(dataClassName ,model);
        FieldSpec dataList = FieldSpec.builder(parameterizedDataList,"mList")
                .addModifiers(PRIVATE)
                .build();
        // create main constructor
        MethodSpec mainConstructor = MethodSpec.constructorBuilder()
                .addParameter(parameterizedDataList , "list")
                .addModifiers(PUBLIC)
                .addStatement("this.mList = list")
                .build();

        // view holder class name
        ClassName vh = ClassName.get("androidx.recyclerview.widget.RecyclerView",
                "ViewHolder");
        // custom viewHolder constructor
        MethodSpec method = MethodSpec.constructorBuilder()
                .addParameter(row , "binding")
                .addModifiers(PUBLIC)
                .addStatement("super(binding.getRoot())")
                .addStatement("this.binding = binding")
                .build();
        // binding field in customViewHolder
        FieldSpec fieldSpec = FieldSpec.builder(row , "binding", PUBLIC).build();

        // customViewHolder Type
        TypeSpec customViewHolder = TypeSpec
                .classBuilder(customVhSimpleName)
                .addModifiers(STATIC)
                .superclass(vh)
                .addField(fieldSpec)
                .addMethod(method)
                .build();

        // create onCreateViewHolder method in base class
        ClassName viewGroup =ClassName.get("android.view", "ViewGroup");
        MethodSpec onCreateViewHolder = MethodSpec.methodBuilder("onCreateViewHolder")
                .addParameter(viewGroup , "parent")
                .addParameter(TypeName.INT ,"viewType")
                .addModifiers(PUBLIC)
                .addStatement("LayoutInflater inflater = LayoutInflater.from(parent.getContext())")
                .addStatement(rowString+" binding = "+rowString+".inflate(inflater, parent, false)")
                .addStatement("return new CustomViewHolder(binding )")
                .addAnnotation(Override.class)
                .returns(vhName)
                .build();

        // create onBindViewHolder method in base class
        MethodSpec onBindViewHolder = MethodSpec.methodBuilder("onBindViewHolder")
                .addParameter(vhName , "holder")
                .addParameter(TypeName.INT ,"position")
                .addModifiers(PUBLIC)
                .addStatement(modelString+" model = mList.get(position)")
                .addStatement("onBind(holder, position , model)")
                .addAnnotation(Override.class)
                .build();

        // create abstract onBind
        MethodSpec onBind = MethodSpec.methodBuilder("onBind")
                .addParameter(vhName , "holder")
                .addParameter(TypeName.INT ,"position")
                .addParameter(model , "model")
                .addModifiers(ABSTRACT)
                .build();
        // create getItemCount method
        MethodSpec getItemCount = MethodSpec.methodBuilder("getItemCount")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.INT)
                .addStatement("return mList.size()")
                .build();

        // add temp var LayoutInflater to import class
        ClassName LayoutInflater =ClassName.get("android.view" ,
                "LayoutInflater");
        FieldSpec layoutInf = FieldSpec.builder(LayoutInflater , "temp").build();
        // base class builder
        TypeSpec baseClass = TypeSpec
                .classBuilder(name)
                .addField(dataList)
                .addField(layoutInf)
                .addModifiers(PUBLIC, ABSTRACT)
                .superclass(parentAdapter)
                .addMethod(mainConstructor)
                .addMethod(onCreateViewHolder)
                .addMethod(onBindViewHolder)
                .addMethod(onBind)
                .addMethod(getItemCount)
                .addType(customViewHolder)
                .build();
        JavaFile javaFile = JavaFile.builder(pkg, baseClass).build();
        return javaFile.toString();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }
}