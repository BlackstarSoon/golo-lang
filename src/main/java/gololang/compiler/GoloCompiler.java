package gololang.compiler;

import gololang.compiler.ir.GoloModule;
import gololang.compiler.parser.ASTCompilationUnit;
import gololang.compiler.parser.GoloParser;
import gololang.compiler.parser.ParseException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class GoloCompiler {

  public final static class Result {

    private final byte[] bytecode;
    private final PackageAndClass packageAndClass;

    public Result(byte[] bytecode, PackageAndClass packageAndClass) {
      this.bytecode = bytecode;
      this.packageAndClass = packageAndClass;
    }

    public byte[] getBytecode() {
      return bytecode;
    }

    public PackageAndClass getPackageAndClass() {
      return packageAndClass;
    }
  }

  private GoloParser parser;

  private GoloParser getParser(InputStream sourceCodeInputStream) {
    if (parser == null) {
      parser = new GoloParser(sourceCodeInputStream);
    } else {
      parser.ReInit(sourceCodeInputStream);
    }
    return parser;
  }

  public Result compile(String goloSourceFilename, InputStream sourceCodeInputStream) throws GoloCompilationException {
    ASTCompilationUnit compilationUnit = null;
    try {
      compilationUnit = getParser(sourceCodeInputStream).CompilationUnit();
    } catch (ParseException e) {
      throw new GoloCompilationException(e);
    }
    ParseTreeToGoloAstVisitor parseTreeToAst = new ParseTreeToGoloAstVisitor();
    GoloModule goloModule = parseTreeToAst.transform(compilationUnit);
    LocalReferenceAssignmentAndVerificationVisitor localReferenceVisitor = new LocalReferenceAssignmentAndVerificationVisitor();
    localReferenceVisitor.visitModule(goloModule);
    JavaBytecodeGenerationGoloIrVisitor bytecodeGenerator = new JavaBytecodeGenerationGoloIrVisitor();
    byte[] bytes = bytecodeGenerator.toBytecode(goloModule, goloSourceFilename);
    return new Result(bytes, goloModule.getPackageAndClass());
  }

  public void compileTo(String goloSourceFilename, InputStream sourceCodeInputStream, File targetFolder) throws GoloCompilationException, IOException {
    if (targetFolder.isFile()) {
      throw new IllegalArgumentException(targetFolder + " already exists and is a file.");
    }
    Result result = compile(goloSourceFilename, sourceCodeInputStream);
    File outputFolder = new File(targetFolder, result.packageAndClass.packageName().replaceAll("\\.", File.separator));
    if (!outputFolder.exists() && !outputFolder.mkdirs()) {
      throw new IOException("mkdir() failed on " + outputFolder);
    }
    File outputFile = new File(outputFolder, result.packageAndClass.className() + ".class");
    try (FileOutputStream out = new FileOutputStream(outputFile)) {
      out.write(result.bytecode);
    }
  }
}
